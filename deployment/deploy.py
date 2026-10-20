#!/usr/bin/env python3
"""Kharcha Tracker deployment script.

Supports local, offline, and remote (SSH) deployment of
kharcha_v1/kharcha_gateway into a Python virtual environment
located at kharcha-tracker/.venv/.

Before deploying, any kharcha service already running -- on the target
port or on a stale port from an earlier deploy (typically 8000) -- is
detected and stopped, so after the update exactly one instance remains,
listening on the requested --port, which is started again.

Backup / restore: every deployment first snapshots the existing
installation (last 3 backups kept) and, if the update or the service
health check fails, automatically restores the previous installation and
(brackets the remote case) restarts it. Backups can also be taken and
restored explicitly with --backup and --restore.

DATABASE SAFETY: tarballs and backups contain code files ONLY. Database
files (flat_file_db/ directories, *.db/*.sqlite* files) are never packed
into an archive, and every tree swap (update or restore) copies them
aside first (.kharcha-preserve/) and copies them back afterwards --
deployment never overwrites or removes the database. The offline package
cache (kharcha-tracker/cache/) is kept across swaps for the same reason.

Remote deployment uses a single SSH connection (paramiko). If password
authentication is needed, the password is prompted ONCE and kept only
in process memory -- it is never written to disk or passed as an
argument.

Usage (run from the repository root):
    python deployment/deploy.py                          # local install + start
    python deployment/deploy.py --production             # local production start
    python deployment/deploy.py --offline                # install from cache if PyPI fails
    python deployment/deploy.py --tarball                # build tarball only
    python deployment/deploy.py --backup                 # backup current installation
    python deployment/deploy.py --restore                # restore latest backup
    python deployment/deploy.py --remote user@host       # full remote deploy
    python deployment/deploy.py --remote user@host --remote-path /opt/kharcha
    python deployment/deploy.py --remote user@host:2222 --remote-start
    python deployment/deploy.py --remote user@host --backup
    python deployment/deploy.py --remote user@host --restore

Tarball layout (written to deployment/kharcha_gateway.tar.gz):
    kharcha-tracker/
    |-- kharcha_v1/...       (application tree)
    +-- requirement.txt
"""

import argparse
import getpass
import os
import re
import shlex
import shutil
import signal
import subprocess
import sys
import tarfile
import time
from datetime import datetime
from pathlib import Path
from typing import Any, NoReturn

try:
    import paramiko
except ImportError:
    paramiko = None
paramiko: Any  # optional dependency; None-guarded in SSHSession.__init__

# ---------------------------------------------------------------------------
# Exit codes (per proposal)
# ---------------------------------------------------------------------------
EXIT_OK = 0
EXIT_UNKNOWN = 1
EXIT_BAD_ARGS = 2
EXIT_VENV_FAIL = 3
EXIT_DEP_FAIL = 4
EXIT_OFFLINE_FAIL = 5
EXIT_SSH_FAIL = 6
EXIT_REMOTE_DEPLOY_FAIL = 7
EXIT_SERVICE_FAIL = 8
EXIT_VALIDATE_FAIL = 9

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
DEPLOY_DIR = Path(__file__).resolve().parent          # .../deployment
REPO_ROOT = DEPLOY_DIR.parent                          # repository root
PROJECT_ROOT = REPO_ROOT / "kharcha-tracker"           # deployment folder
KHARCHA_DIR = PROJECT_ROOT / "kharcha_v1" / "kharcha_gateway"
KHARCHA_V1_DIR = PROJECT_ROOT / "kharcha_v1"
REQUIREMENTS_FILE = PROJECT_ROOT / "requirement.txt"
VENV_DIR = PROJECT_ROOT / ".venv"                       # venv lives here
CACHE_DIR = PROJECT_ROOT / "cache" / "pip-packages"
TARBALL_NAME = "kharcha_gateway.tar.gz"
TARBALL_PATH = DEPLOY_DIR / TARBALL_NAME                # output in deployment/
DEFAULT_REMOTE_PATH = "/var/www/kharcha-tracker"
RUN_MODULE = "kharcha_v1.kharcha_gateway.app.main:app"

# Backup / restore
BACKUP_KEEP = 3                              # rotation: keep newest N backups
BACKUPS_DIR = DEPLOY_DIR / "backups"         # local backups live here
BACKUP_PREFIX = "kharcha-tracker-"

EXCLUDE_PATTERNS = {"__pycache__", ".DS_Store", ".pytest_cache", ".git", ".venv", "venv",
                    "flat_file_db"}

# Database / runtime-data rules. DB files are NEVER packed into tarballs or
# backups, and NEVER overwritten by update or restore: every tree swap copies
# them aside first (.kharcha-preserve/) and copies them back afterwards.
DB_DIR_NAMES = {"flat_file_db"}
DB_FILE_SUFFIXES = (".db", ".sqlite", ".sqlite3", ".sqlite-shm", ".sqlite-wal")
# Top-level entries of kharcha-tracker/ that are not code and must survive a
# tree swap (.venv is kept separately by the swap logic itself).
SWAP_KEEP_NAMES = {"cache"}

# Copy-based DB preservation, executed via `python3 -c` both locally and on
# the remote host. argv: <action: save|restore> <root: .../kharcha-tracker>.
# save   = copy DB files to <root>/../.kharcha-preserve (originals untouched)
# restore= copy them back over the swapped tree, then drop the preserve dir.
# The source must contain NO single quotes (embedded with shlex.quote).
DB_PRESERVE_SRC = """\
import os
import shutil
import sys

action = sys.argv[1]
root = os.path.abspath(sys.argv[2])
base = os.path.dirname(root)
pres = os.path.join(base, ".kharcha-preserve")
DB_DIRS = {"flat_file_db"}
DB_SUFFIXES = (".db", ".sqlite", ".sqlite3", ".sqlite-shm", ".sqlite-wal")


def is_db(name):
    if name in DB_DIRS:
        return True
    for suffix in DB_SUFFIXES:
        if name.endswith(suffix):
            return True
    return False


def copy_item(src, dst):
    if os.path.isdir(src):
        shutil.copytree(src, dst, dirs_exist_ok=True)
    else:
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)


if action == "save":
    if not os.path.isdir(root):
        sys.exit(0)
    for dirpath, dirnames, filenames in os.walk(root):
        rel = os.path.relpath(dirpath, root)
        parts = [] if rel == "." else rel.split(os.sep)
        if parts and parts[0] == ".venv":
            dirnames[:] = []
            continue
        for d in list(dirnames):
            if is_db(d):
                src = os.path.join(dirpath, d)
                dst = os.path.join(pres, os.path.relpath(src, root))
                copy_item(src, dst)
                dirnames.remove(d)
        for f in filenames:
            if is_db(f):
                src = os.path.join(dirpath, f)
                dst = os.path.join(pres, os.path.relpath(src, root))
                copy_item(src, dst)
elif action == "restore":
    if not os.path.isdir(pres):
        sys.exit(0)
    for dirpath, dirnames, filenames in os.walk(pres, topdown=False):
        for f in filenames:
            src = os.path.join(dirpath, f)
            dst = os.path.join(root, os.path.relpath(src, pres))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            copy_item(src, dst)
        for d in dirnames:
            src = os.path.join(dirpath, d)
            dst = os.path.join(root, os.path.relpath(src, pres))
            if not os.path.isdir(dst):
                shutil.move(src, dst)
    shutil.rmtree(pres, ignore_errors=True)
else:
    sys.exit(2)
"""


def _db_preserve(action: str, root: Path) -> None:
    """Local runner for DB_PRESERVE_SRC (same script the remote uses)."""
    result = subprocess.run(
        [sys.executable, "-c", DB_PRESERVE_SRC, action, str(root)],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        fail(f"DB preserve {action} failed for {root}: {result.stderr.strip()}",
             EXIT_VALIDATE_FAIL)


def _remote_preserve_cmd(action: str, remote_path: str) -> str:
    root = shlex.quote(f"{remote_path}/kharcha-tracker")
    return f"python3 -c {shlex.quote(DB_PRESERVE_SRC)} {action} {root}"

# Remote snippet that detects a kharcha/uvicorn service on a port, prints
# RUNNING=1/0, and stops it (SIGTERM, then SIGKILL after 10s). Contains no
# single quotes so it can be embedded in a python3 -c '...' invocation.
REMOTE_STOP_SCRIPT = """
import os, signal, subprocess, time
port = __PORT__
pids = []
try:
    r = subprocess.run(["lsof", "-nP", "-iTCP:" + str(port), "-sTCP:LISTEN", "-t"],
                       capture_output=True, text=True)
    pids = [int(x) for x in r.stdout.split() if x.strip().isdigit()]
except FileNotFoundError:
    pass
ours = []
for pid in pids:
    try:
        r2 = subprocess.run(["ps", "-p", str(pid), "-o", "command="],
                            capture_output=True, text=True)
        cmd = r2.stdout.lower()
    except Exception:
        cmd = ""
    if "uvicorn" in cmd or "kharcha" in cmd:
        ours.append(pid)
print("RUNNING=" + ("1" if ours else "0"))
if pids and not ours:
    print("WARN: port " + str(port) + " used by unrelated process(es): " + str(pids))
for pid in ours:
    try:
        os.kill(pid, signal.SIGTERM)
        print("SIGTERM sent to " + str(pid))
    except ProcessLookupError:
        pass
deadline = time.time() + 10
while ours and time.time() < deadline:
    alive = []
    for pid in ours:
        try:
            os.kill(pid, 0)
            alive.append(pid)
        except ProcessLookupError:
            pass
    ours = alive
    if ours:
        time.sleep(0.5)
for pid in ours:
    try:
        os.kill(pid, signal.SIGKILL)
        print("SIGKILL sent to " + str(pid))
    except ProcessLookupError:
        pass
"""

# Remote snippet that stops kharcha services listening on ANY port except
# the deployment target (stale instances from earlier deploys). Matches only
# processes whose command mentions "kharcha"; argv[1] = target port.
REMOTE_STOP_ANY_SCRIPT = """
import os, signal, subprocess, sys, time
target = int(sys.argv[1])
try:
    r = subprocess.run(["lsof", "-nP", "-iTCP", "-sTCP:LISTEN"],
                       capture_output=True, text=True)
except FileNotFoundError:
    print("OTHERS_STOPPED=0")
    raise SystemExit(0)
listeners = []
for line in r.stdout.splitlines()[1:]:
    tokens = line.split()
    if len(tokens) < 2:
        continue
    port = None
    for i, tok in enumerate(tokens):
        if tok.startswith("(LISTEN)") and i > 0:
            cand = tokens[i - 1]
            if ":" in cand:
                try:
                    port = int(cand.rsplit(":", 1)[-1])
                except ValueError:
                    port = None
            break
    if port is None:
        continue
    try:
        pid = int(tokens[1])
    except ValueError:
        continue
    listeners.append((pid, port))
stopped = 0
for pid, port in listeners:
    if port == target:
        continue
    try:
        r2 = subprocess.run(["ps", "-p", str(pid), "-o", "command="],
                            capture_output=True, text=True)
    except FileNotFoundError:
        continue
    if "kharcha" not in r2.stdout.lower():
        continue
    print("OTHER_STOP port=" + str(port) + " pid=" + str(pid))
    try:
        os.kill(pid, signal.SIGTERM)
    except ProcessLookupError:
        continue
    deadline = time.time() + 10
    while time.time() < deadline:
        try:
            os.kill(pid, 0)
            time.sleep(0.5)
        except ProcessLookupError:
            break
    else:
        try:
            os.kill(pid, signal.SIGKILL)
            print("SIGKILL pid=" + str(pid))
        except ProcessLookupError:
            pass
    stopped += 1
print("OTHERS_STOPPED=" + str(stopped))
"""


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def log(msg: str) -> None:
    print(f"[deploy] {msg}", flush=True)


def fail(msg: str, code: int = EXIT_UNKNOWN) -> NoReturn:
    print(f"[deploy] ERROR: {msg}", file=sys.stderr)
    sys.exit(code)


def run(cmd: list[str], cwd: Path | None = None, check: bool = True,
        env: dict | None = None, capture: bool = False) -> subprocess.CompletedProcess:
    log(f"$ {' '.join(cmd)}")
    merged_env = {**os.environ, **(env or {})}
    try:
        result = subprocess.run(
            cmd, cwd=cwd, check=check, env=merged_env,
            capture_output=capture, text=True,
        )
        if capture and result.stdout:
            print(result.stdout)
        return result
    except subprocess.CalledProcessError as exc:
        fail(f"Command failed: {' '.join(cmd)}\n{exc}", EXIT_DEP_FAIL)
        raise  # unreachable, for type-checkers
    except FileNotFoundError:
        fail(f"Command not found: {cmd[0]}", EXIT_DEP_FAIL)
        raise


# ---------------------------------------------------------------------------
# Python / venv
# ---------------------------------------------------------------------------
def find_python() -> str:
    """Find a suitable python interpreter (3.11+ preferred, 3.10+ acceptable)."""
    for candidate in (os.environ.get("PYTHON_VERSION", ""), "python3", "python"):
        if not candidate:
            continue
        path = shutil.which(candidate)
        if not path:
            continue
        try:
            out = subprocess.run(
                [path, "-c", "import sys; print(sys.version_info[:2])"],
                capture_output=True, text=True, check=True,
            ).stdout.strip()
            major, minor = (int(x) for x in out.strip("()").split(","))
            if (major, minor) >= (3, 10):
                return path
        except Exception:
            continue
    fail("No suitable Python (>=3.10) found. Install Python 3.11+.", EXIT_VENV_FAIL)
    raise


def venv_bin(name: str) -> Path:
    """Return path to an executable inside kharcha-tracker/.venv."""
    if os.name == "nt":
        return VENV_DIR / "Scripts" / name
    return VENV_DIR / "bin" / name


def create_venv(python: str) -> None:
    if (VENV_DIR / "pyvenv.cfg").exists():
        log(f"Virtual environment already exists: {VENV_DIR}")
        return
    log(f"Creating virtual environment: {VENV_DIR}")
    try:
        run([python, "-m", "venv", str(VENV_DIR)])
    except Exception:
        fail("Virtual environment creation failed.", EXIT_VENV_FAIL)


def upgrade_pip() -> None:
    run([str(venv_bin("python")), "-m", "pip", "install", "--upgrade", "pip"],
        check=False)


# ---------------------------------------------------------------------------
# Requirements parsing
# ---------------------------------------------------------------------------
def parse_requirements() -> list[str]:
    if not REQUIREMENTS_FILE.exists():
        fail(f"Requirements file not found: {REQUIREMENTS_FILE}", EXIT_BAD_ARGS)
    packages: list[str] = []
    for line in REQUIREMENTS_FILE.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and not line.startswith("-"):
            match = re.match(r"^([A-Za-z0-9_.-]+)", line)
            if match:
                packages.append(match.group(1))
    return packages


# ---------------------------------------------------------------------------
# Dependency installation
# ---------------------------------------------------------------------------
def install_online() -> bool:
    """Try installing from PyPI. Returns True on success."""
    log("Installing dependencies from PyPI ...")
    result = subprocess.run(
        [str(venv_bin("python")), "-m", "pip", "install", "-r", str(REQUIREMENTS_FILE)],
        capture_output=True, text=True,
    )
    if result.returncode == 0:
        if result.stdout:
            print(result.stdout)
        log("Dependencies installed from PyPI.")
        return True
    log(f"PyPI install failed:\n{result.stderr}")
    return False


def download_to_cache() -> None:
    """Download all requirements into cache/pip-packages/ using pip download."""
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    log(f"Downloading packages to cache: {CACHE_DIR}")
    result = subprocess.run(
        [
            str(venv_bin("python")), "-m", "pip", "download",
            "-r", str(REQUIREMENTS_FILE),
            "-d", str(CACHE_DIR),
        ],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        fail(f"pip download to cache failed:\n{result.stderr}", EXIT_OFFLINE_FAIL)
    log("Packages cached successfully.")


def install_from_cache() -> bool:
    """Try installing from local cache. Returns True on success."""
    if not CACHE_DIR.exists() or not any(CACHE_DIR.iterdir()):
        log("Cache directory is empty or missing.")
        return False
    log(f"Installing dependencies from cache: {CACHE_DIR}")
    result = subprocess.run(
        [
            str(venv_bin("python")), "-m", "pip", "install",
            "--no-index", "--find-links", str(CACHE_DIR),
            "-r", str(REQUIREMENTS_FILE),
        ],
        capture_output=True, text=True,
    )
    if result.returncode == 0:
        if result.stdout:
            print(result.stdout)
        log("Dependencies installed from cache.")
        return True
    log(f"Cache install failed:\n{result.stderr}")
    return False


def install_dependencies(offline: bool) -> None:
    if offline:
        if install_online():
            return
        log("Offline mode: falling back to local cache ...")
        if install_from_cache():
            return
        log("Cache miss. Attempting to populate cache from PyPI ...")
        try:
            download_to_cache()
        except SystemExit:
            pass
        if install_from_cache():
            return
        fail(
            "Offline deployment failed: packages unavailable from PyPI and cache.\n"
            "Run 'pip download -r requirement.txt -d cache/pip-packages' on a "
            "connected machine and copy the cache directory here.",
            EXIT_OFFLINE_FAIL,
        )
    else:
        if install_online():
            return
        log("Online install failed. Attempting to populate cache and retry ...")
        try:
            download_to_cache()
        except SystemExit:
            pass
        if install_from_cache():
            return
        fail("Dependency installation failed (online and offline).", EXIT_DEP_FAIL)


# ---------------------------------------------------------------------------
# Service detection / stop (local)
# ---------------------------------------------------------------------------
def _port_listen_pids(port: int) -> list[int]:
    """PIDs listening on the given TCP port (empty if lsof unavailable)."""
    try:
        result = subprocess.run(
            ["lsof", "-nP", "-iTCP:" + str(port), "-sTCP:LISTEN", "-t"],
            capture_output=True, text=True,
        )
    except FileNotFoundError:
        return []
    if result.returncode not in (0, 1):
        return []
    return [int(x) for x in result.stdout.split() if x.strip().isdigit()]


def _pid_cmdline(pid: int) -> str:
    try:
        result = subprocess.run(
            ["ps", "-p", str(pid), "-o", "command="],
            capture_output=True, text=True,
        )
        return result.stdout
    except Exception:
        return ""


def _pid_alive(pid: int) -> bool:
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def detect_and_stop_service(port: int) -> bool:
    """Detect a kharcha/uvicorn service on *port* and stop it.

    Returns True if such a service was found and stopped. Unrelated
    processes on the port are left alone (a warning is logged).
    """
    pids = _port_listen_pids(port)
    if not pids:
        log(f"No process is listening on port {port}.")
        return False

    ours = [p for p in pids if re.search(r"uvicorn|kharcha", _pid_cmdline(p), re.I)]
    if not ours:
        log(f"WARNING: port {port} is in use by unrelated process(es) {pids}; "
            "not stopping them -- service start may fail.")
        return False

    log(f"Service already running on port {port} (pid {', '.join(map(str, ours))}). "
        "Stopping it before update ...")
    for pid in ours:
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    deadline = time.time() + 10
    while time.time() < deadline:
        ours = [p for p in ours if _pid_alive(p)]
        if not ours:
            break
        time.sleep(0.5)
    for pid in ours:
        try:
            os.kill(pid, signal.SIGKILL)
            log(f"SIGKILL sent to pid {pid}.")
        except ProcessLookupError:
            pass
    log("Existing service stopped.")
    return True


def _scan_listeners() -> list[tuple[int, int, str]]:
    """All TCP listeners as (pid, port, cmdline).

    Used to find stale kharcha instances running on ports OTHER than the
    deployment target -- they would otherwise keep serving old code.
    """
    try:
        result = subprocess.run(
            ["lsof", "-nP", "-iTCP", "-sTCP:LISTEN"],
            capture_output=True, text=True,
        )
    except FileNotFoundError:
        return []
    listeners: list[tuple[int, int, str]] = []
    for line in result.stdout.splitlines()[1:]:
        tokens = line.split()
        if len(tokens) < 2:
            continue
        port: int | None = None
        for i, tok in enumerate(tokens):
            if tok.startswith("(LISTEN)") and i > 0:
                cand = tokens[i - 1]
                if ":" in cand:
                    try:
                        port = int(cand.rsplit(":", 1)[-1])
                    except ValueError:
                        port = None
                break
        if port is None:
            continue
        try:
            pid = int(tokens[1])
        except ValueError:
            continue
        listeners.append((pid, port, _pid_cmdline(pid)))
    return listeners


def detect_and_stop_other_ports(target: int) -> int:
    """Stop kharcha services listening on ports other than *target*.

    Deploying with --port N must leave exactly one instance -- on N.
    A stale instance from an earlier deploy (e.g. 8000) would otherwise
    keep serving old code. Only processes whose command mentions
    'kharcha' are stopped; anything else is left alone.
    """
    stopped = 0
    for pid, port, cmdline in _scan_listeners():
        if port == target or "kharcha" not in cmdline.lower():
            continue
        log(f"Stopping kharcha service on port {port} (pid {pid}) -- "
            f"target port is {target}.")
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            continue
        deadline = time.time() + 10
        while _pid_alive(pid) and time.time() < deadline:
            time.sleep(0.5)
        if _pid_alive(pid):
            try:
                os.kill(pid, signal.SIGKILL)
                log(f"SIGKILL sent to pid {pid}.")
            except ProcessLookupError:
                pass
        stopped += 1
    if stopped:
        log(f"Stopped {stopped} kharcha instance(s) on other ports.")
    return stopped


# ---------------------------------------------------------------------------
# Tarball
# ---------------------------------------------------------------------------
def _filter_tarinfo(tarinfo: tarfile.TarInfo) -> tarfile.TarInfo | None:
    """Code-only filter for tarballs AND backups: never packs database
    files (flat_file_db/ dirs, *.db/*.sqlite* files), venvs, or caches."""
    name = Path(tarinfo.name).name
    if name.endswith(DB_FILE_SUFFIXES):
        return None
    parts = Path(tarinfo.name).parts
    if any(part in EXCLUDE_PATTERNS for part in parts):
        return None
    if any(part in DB_DIR_NAMES for part in parts):
        return None
    tarinfo.uid = tarinfo.gid = 0
    tarinfo.uname = tarinfo.gname = "root"
    return tarinfo


def create_tarball(output: Path | None = None) -> Path:
    """Build the deployment tarball.

    Layout (root folder is kharcha-tracker/):
        kharcha-tracker/kharcha_v1/...   application tree
        kharcha-tracker/requirement.txt  dependencies

    Output defaults to deployment/kharcha_gateway.tar.gz.
    """
    output = output or TARBALL_PATH
    if not KHARCHA_V1_DIR.exists():
        fail(f"Source directory not found: {KHARCHA_V1_DIR}", EXIT_BAD_ARGS)
    if not REQUIREMENTS_FILE.exists():
        fail(f"Requirements file not found: {REQUIREMENTS_FILE}", EXIT_BAD_ARGS)
    output.parent.mkdir(parents=True, exist_ok=True)
    log(f"Creating tarball: {output}")
    with tarfile.open(output, "w:gz") as tar:
        tar.add(str(KHARCHA_V1_DIR), arcname="kharcha-tracker/kharcha_v1",
                filter=_filter_tarinfo)
        tar.add(str(REQUIREMENTS_FILE), arcname="kharcha-tracker/requirement.txt",
                filter=_filter_tarinfo)
    size_kb = output.stat().st_size / 1024
    log(f"Tarball created ({size_kb:.1f} KB): {output}")
    return output


# ---------------------------------------------------------------------------
# Local service start
# ---------------------------------------------------------------------------
def start_local(production: bool, port: int) -> None:
    python_bin = str(venv_bin("python"))
    cmd = [
        python_bin, "-m", "uvicorn", RUN_MODULE,
        "--host", "0.0.0.0",
        "--port", str(port),
    ]
    if not production:
        cmd.append("--reload")
    storage = os.environ.get("KHARCHA_STORAGE", "flat_file")
    env = {"KHARCHA_STORAGE": storage}
    log(f"Starting service (storage={storage}) ...")
    log(f"  Run target : {RUN_MODULE}")
    log(f"  URL        : http://0.0.0.0:{port}")
    try:
        run(cmd, cwd=PROJECT_ROOT, env=env)
    except KeyboardInterrupt:
        log("Service stopped.")
    except Exception:
        fail("Service startup failed.", EXIT_SERVICE_FAIL)


def start_local_detached(port: int) -> None:
    """Restart the service in the background (used after a restore)."""
    python_bin = str(venv_bin("python"))
    storage = os.environ.get("KHARCHA_STORAGE", "flat_file")
    log_path = PROJECT_ROOT / "server.log"
    cmd = [
        python_bin, "-m", "uvicorn", RUN_MODULE,
        "--host", "0.0.0.0",
        "--port", str(port),
    ]
    log_fh: Any = None
    try:
        log_fh = open(log_path, "ab")
    except OSError:
        pass
    try:
        proc = subprocess.Popen(
            cmd, cwd=PROJECT_ROOT,
            env={**os.environ, "KHARCHA_STORAGE": storage},
            stdout=log_fh if log_fh is not None else subprocess.DEVNULL,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    finally:
        if log_fh is not None:
            log_fh.close()
    log(f"Service started in background (pid {proc.pid}); log: {log_path}")


# ---------------------------------------------------------------------------
# Backup / restore (local)
# ---------------------------------------------------------------------------
def _backup_stamp() -> str:
    return datetime.now().strftime("%Y%m%d-%H%M%S-%f")


def local_backup() -> Path:
    """Snapshot kharcha-tracker/ (code only -- no .venv, no DB files) into
    deployment/backups/, then rotate to keep the newest BACKUP_KEEP.

    Database files are excluded on purpose: they are user data, never
    shipped and never restored; the restore path preserves them in place.
    """
    if not PROJECT_ROOT.exists():
        fail(f"Nothing to back up: {PROJECT_ROOT} does not exist.",
             EXIT_VALIDATE_FAIL)
    BACKUPS_DIR.mkdir(parents=True, exist_ok=True)
    name = f"{BACKUP_PREFIX}{_backup_stamp()}.tar.gz"
    path = BACKUPS_DIR / name
    log(f"Backing up {PROJECT_ROOT.name}/ -> {path}")
    with tarfile.open(path, "w:gz") as tar:
        tar.add(str(PROJECT_ROOT), arcname="kharcha-tracker",
                filter=_filter_tarinfo)
    size_kb = path.stat().st_size / 1024
    log(f"Backup created ({size_kb:.1f} KB): {name}")
    # Rotate: keep the newest BACKUP_KEEP
    backups = sorted(BACKUPS_DIR.glob(f"{BACKUP_PREFIX}*.tar.gz"),
                     key=lambda p: p.stat().st_mtime, reverse=True)
    for stale in backups[BACKUP_KEEP:]:
        stale.unlink(missing_ok=True)
        log(f"Rotated out old backup: {stale.name}")
    return path


def latest_local_backup() -> Path | None:
    backups = sorted(BACKUPS_DIR.glob(f"{BACKUP_PREFIX}*.tar.gz"),
                     key=lambda p: p.stat().st_mtime, reverse=True)
    return backups[0] if backups else None


def _is_db_member(name: str) -> bool:
    """True if an archive member path belongs to the database."""
    parts = Path(name).parts
    if any(part in DB_DIR_NAMES for part in parts):
        return True
    return Path(name).name.endswith(DB_FILE_SUFFIXES)


def local_restore(backup: Path) -> None:
    """Restore kharcha-tracker/ from *backup* (code files only).

    Everything except .venv/ and cache/ is replaced with the backup
    contents. Database files are copied aside BEFORE the swap and copied
    back AFTER it -- a restore never modifies the database. Any DB paths
    inside the archive (old/hand-made backups) are skipped during
    extraction. Dependencies are then re-synced with the restored
    requirement.txt.
    """
    if not backup.exists():
        fail(f"Backup not found: {backup}", EXIT_VALIDATE_FAIL)
    log(f"Restoring from {backup.name} ...")
    staging = DEPLOY_DIR / ".restore-staging"
    if staging.exists():
        shutil.rmtree(staging)
    staging.mkdir(parents=True)
    _db_preserve("save", PROJECT_ROOT)
    try:
        with tarfile.open(backup, "r:gz") as tar:
            for member in tar.getmembers():
                if _is_db_member(member.name):
                    continue
                tar.extract(member, staging)
        src = staging / "kharcha-tracker"
        if not src.exists():
            fail("Backup archive is missing the kharcha-tracker/ folder.",
                 EXIT_VALIDATE_FAIL)
        if PROJECT_ROOT.exists():
            for child in PROJECT_ROOT.iterdir():
                if child.name == ".venv" or child.name in SWAP_KEEP_NAMES:
                    continue  # keep venv and offline cache in place
                if child.is_dir() and not child.is_symlink():
                    shutil.rmtree(child)
                else:
                    child.unlink()
        shutil.copytree(src, PROJECT_ROOT, dirs_exist_ok=True)
    finally:
        _db_preserve("restore", PROJECT_ROOT)
        shutil.rmtree(staging, ignore_errors=True)
    # Re-sync dependencies with the restored requirement.txt
    if (VENV_DIR / "pyvenv.cfg").exists():
        log("Re-syncing dependencies with restored requirement.txt ...")
        result = subprocess.run(
            [str(venv_bin("python")), "-m", "pip", "install", "-r",
             str(REQUIREMENTS_FILE)],
            capture_output=True, text=True,
        )
        if result.returncode != 0:
            log(f"WARNING: dependency re-sync failed:\n{result.stderr}\n"
                "Files were restored; install requirements manually.")
        else:
            log("Dependencies re-synced.")
    log(f"Restore complete from {backup.name}.")


def local_restore_latest() -> None:
    """Standalone local restore: stop service if running, restore, and
    restart it if it had been running (unless --no-start)."""
    backup = latest_local_backup()
    if backup is None:
        fail(f"No backups found in {BACKUPS_DIR}. "
             "Create one first with --backup.", EXIT_VALIDATE_FAIL)
    port = int(os.environ.get("KHARCHA_PORT", "8000"))
    no_start = os.environ.get("KHARCHA_NO_START") == "1"
    was_running = False
    if not no_start:
        target_running = detect_and_stop_service(port)
        others_stopped = detect_and_stop_other_ports(port)
        was_running = target_running or others_stopped > 0
    local_restore(backup)
    if was_running:
        start_local_detached(port)


# ---------------------------------------------------------------------------
# Remote (SSH) deployment via paramiko -- one connection, one password
# ---------------------------------------------------------------------------
def parse_remote(value: str) -> tuple[str, str, int]:
    """Parse user@host[:port] into (user, host, port)."""
    port = 22
    if ":" in value and value.count(":") == 1:
        hostpart, port_str = value.rsplit(":", 1)
        if port_str.isdigit():
            port = int(port_str)
        else:
            hostpart = value
    else:
        hostpart = value
    if "@" in hostpart:
        user, host = hostpart.split("@", 1)
    else:
        user = ""
        host = hostpart
    if not host:
        fail("Invalid remote specification. Use user@host[:port]", EXIT_BAD_ARGS)
    return user, host, port


class SSHSession:
    """One SSH connection reused for all remote commands and file transfer.

    If the server requires password authentication, the password is
    prompted exactly ONCE via getpass (not echoed) and held only in this
    object's memory for the lifetime of the process. It is never written
    to disk and never passed as a command-line argument.
    """

    def __init__(self, host: str, port: int, user: str):
        if paramiko is None:
            fail(
                "Remote deployment requires paramiko.\n"
                "Install it with: python3 -m pip install 'paramiko' 'cryptography<45'",
                EXIT_BAD_ARGS,
            )
        assert paramiko is not None  # narrowed for type checkers
        self.host = host
        self.port = port
        self.user = user or None
        self.key_path = os.environ.get("SSH_KEY_PATH") or None
        self.password: str | None = None
        self._password_asked = False
        self.client = paramiko.SSHClient()
        self.client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    # -- connection ---------------------------------------------------------
    def _open(self) -> None:
        self.client.connect(
            hostname=self.host,
            port=self.port,
            username=self.user,
            key_filename=self.key_path,
            password=self.password,
            timeout=15,
            banner_timeout=15,
            auth_timeout=15,
            allow_agent=self.password is None,
            look_for_keys=True,
        )

    def _prompt_password(self) -> str:
        who = f"{self.user}@{self.host}" if self.user else self.host
        try:
            password = getpass.getpass(f"SSH password for {who}: ")
        except (EOFError, KeyboardInterrupt):
            fail(
                "Password prompt cancelled. Use SSH key authentication "
                "(set SSH_KEY_PATH) if no terminal is available.",
                EXIT_SSH_FAIL,
            )
        if not password:
            fail("Empty SSH password.", EXIT_SSH_FAIL)
        log("Password captured; keeping it in memory only for this session.")
        return password

    def connect(self, prompt_allowed: bool = True) -> None:
        last_exc: Exception | None = None
        for attempt in range(1, 4):
            try:
                self._open()
                auth = "password" if self.password else "key/agent"
                log(f"Connected to {self.host}:{self.port} ({auth} auth).")
                return
            except paramiko.AuthenticationException as exc:
                last_exc = exc
                if prompt_allowed and not self._password_asked:
                    # The one and only password prompt for this run.
                    self.password = self._prompt_password()
                    self._password_asked = True
                    continue
                fail(
                    f"SSH authentication to {self.host} failed. "
                    "Check username, password, and SSH_KEY_PATH.",
                    EXIT_SSH_FAIL,
                )
            except (paramiko.SSHException, OSError) as exc:
                last_exc = exc
                # No key/agent available: fall back to the one-time prompt.
                if (prompt_allowed and not self._password_asked
                        and "No authentication methods available" in str(exc)):
                    self.password = self._prompt_password()
                    self._password_asked = True
                    continue
                if attempt < 3:
                    wait = 2 ** attempt
                    log(f"Connection attempt {attempt}/3 failed ({exc}); "
                        f"retrying in {wait}s ...")
                    time.sleep(wait)
        fail(f"Unable to connect to {self.host}:{self.port}: {last_exc}", EXIT_SSH_FAIL)

    def reconnect(self) -> None:
        """Re-establish the connection, reusing the in-memory password."""
        try:
            self.client.close()
        except Exception:
            pass
        self.client = paramiko.SSHClient()
        self.client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self.connect(prompt_allowed=False)

    def close(self) -> None:
        try:
            self.client.close()
        except Exception:
            pass

    # -- operations ---------------------------------------------------------
    def exec(self, cmd: str, check: bool = True,
             timeout: int = 1800) -> tuple[str, str, int]:
        """Run a remote command. Returns (stdout, stderr, returncode)."""
        for attempt in range(1, 4):
            try:
                _stdin, stdout, stderr = self.client.exec_command(cmd, timeout=timeout)
                out = stdout.read().decode(errors="replace")
                err = stderr.read().decode(errors="replace")
                rc = stdout.channel.recv_exit_status()
                if out:
                    print(out, end="" if out.endswith("\n") else "\n")
                if rc != 0 and err:
                    print(err, file=sys.stderr, end="" if err.endswith("\n") else "\n")
                if rc == 0 or not check:
                    return out, err, rc
                fail(f"Remote command failed (rc={rc}):\n{cmd}\n{err}",
                     EXIT_REMOTE_DEPLOY_FAIL)
            except (paramiko.SSHException, OSError) as exc:
                if attempt == 3:
                    fail(f"SSH connection lost during remote command: {exc}", EXIT_SSH_FAIL)
                log(f"SSH connection error ({exc}); reconnecting ...")
                self.reconnect()
        raise AssertionError("unreachable")

    def put(self, local: Path, remote: str) -> None:
        log(f"Uploading {local.name} -> {remote} ...")
        for attempt in range(1, 4):
            try:
                sftp = self.client.open_sftp()
                try:
                    sftp.put(str(local), remote)
                finally:
                    sftp.close()
                return
            except (paramiko.SSHException, OSError) as exc:
                if attempt == 3:
                    fail(f"File upload failed: {exc}", EXIT_REMOTE_DEPLOY_FAIL)
                log(f"Upload error ({exc}); reconnecting ...")
                self.reconnect()


def remote_detect_and_stop(session: SSHSession, port: int) -> bool:
    """Detect and stop a kharcha/uvicorn service on the remote port."""
    script = REMOTE_STOP_SCRIPT.replace("__PORT__", str(port))
    out, _err, rc = session.exec(f"python3 -c {shlex.quote(script)}", check=False)
    if rc != 0:
        log(f"Remote port check failed (rc={rc}); continuing.")
        return False
    return "RUNNING=1" in out


def remote_stop_other_kharcha(session: SSHSession, target: int) -> int:
    """Stop remote kharcha instances on ports OTHER than *target*.

    Returns the number stopped. A stale instance (typically 8000 from an
    earlier default-port deploy) would otherwise keep serving old code
    after deploying with --port N.
    """
    out, _err, rc = session.exec(
        f"python3 -c {shlex.quote(REMOTE_STOP_ANY_SCRIPT)} {target}",
        check=False,
    )
    if rc != 0:
        log(f"Remote cross-port scan failed (rc={rc}); continuing.")
        return 0
    for line in out.splitlines():
        if line.startswith("OTHER_STOP "):
            log(f"Remote: {line.replace('OTHER_STOP ', 'stopping kharcha service on ')}")
    stopped = 0
    for line in out.splitlines():
        if line.startswith("OTHERS_STOPPED="):
            try:
                stopped = int(line.split("=", 1)[1])
            except ValueError:
                stopped = 0
    if stopped:
        log(f"Stopped {stopped} stale kharcha instance(s) on other ports "
            f"(target port {target}).")
    return stopped


def remote_dir_exists(session: SSHSession, path: str) -> bool:
    _out, _err, rc = session.exec(f"test -d {shlex.quote(path)}", check=False)
    return rc == 0


# -- remote command builders (kept pure so they can be tested locally) ------
# python3 backup builder: identical exclusion rules to the local tarball
# (no .venv, no DB files) -- avoids tar --exclude path-matching ambiguity.
_REMOTE_BACKUP_SRC = f"""\
import sys
import tarfile
from pathlib import Path

EXCLUDE = {EXCLUDE_PATTERNS!r}
DB_DIRS = {DB_DIR_NAMES!r}
DB_SUFFIXES = {DB_FILE_SUFFIXES!r}


def keep(tarinfo):
    parts = Path(tarinfo.name).parts
    if any(p in EXCLUDE for p in parts):
        return None
    if any(p in DB_DIRS for p in parts):
        return None
    name = parts[-1] if parts else ""
    if name.endswith(DB_SUFFIXES):
        return None
    tarinfo.uid = tarinfo.gid = 0
    tarinfo.uname = tarinfo.gname = "root"
    return tarinfo


src, out = sys.argv[1], sys.argv[2]
with tarfile.open(out, "w:gz") as archive:
    archive.add(src, arcname="kharcha-tracker", filter=keep)
"""


def _remote_preserve_guard(remote_path: str) -> str:
    """Shell `trap` line: if the script dies mid-swap (set -e, lost
    connection), copy the preserved DB back before exiting."""
    body = (f"python3 -c {shlex.quote(DB_PRESERVE_SRC)} restore "
            f"{shlex.quote(f'{remote_path}/kharcha-tracker')} "
            f">/dev/null 2>&1 || true")
    return f"trap {shlex.quote(body)} EXIT"


def _remote_backup_cmd(remote_path: str, name: str) -> str:
    """Archive the remote kharcha-tracker/ into <remote-path>/backups/<name>.
    Code only: excludes .venv, flat_file_db/ and *.db/*.sqlite* files."""
    rp = shlex.quote(remote_path)
    src = shlex.quote(f"{remote_path}/kharcha-tracker")
    out = shlex.quote(f"{remote_path}/backups/{name}")
    return (f"mkdir -p {rp}/backups && "
            f"python3 -c {shlex.quote(_REMOTE_BACKUP_SRC)} {src} {out}")


def _remote_rotate_cmd(remote_path: str) -> str:
    """Keep only the newest BACKUP_KEEP remote backups."""
    glob = f"{remote_path}/backups/{BACKUP_PREFIX}*.tar.gz"
    return (f"i=0; for f in $(ls -t {glob} 2>/dev/null); do "
            f"i=$((i+1)); if [ $i -gt {BACKUP_KEEP} ]; then rm -f \"$f\"; fi; done")


def _remote_latest_backup_cmd(remote_path: str) -> str:
    glob = f"{remote_path}/backups/{BACKUP_PREFIX}*.tar.gz"
    return f"ls -t {glob} 2>/dev/null | head -n 1"


def _remote_restore_script(remote_path: str, name: str) -> str:
    """Swap the remote installation back to the backup archive.

    The existing .venv and cache/ are kept. Database files are copied
    aside before the swap and copied back after it -- the restore never
    modifies the database. Dependencies are re-synced with the restored
    requirement.txt.
    """
    rp = shlex.quote(remote_path)
    save = _remote_preserve_cmd("save", remote_path)
    restore = _remote_preserve_cmd("restore", remote_path)
    guard = _remote_preserve_guard(remote_path)
    return f"""set -e
cd {rp}
if [ ! -f "backups/{name}" ]; then echo "ERROR: backup not found: backups/{name}" >&2; exit 1; fi
rm -rf .restore-staging
mkdir -p .restore-staging
tar -xzf "backups/{name}" -C .restore-staging
if [ ! -d .restore-staging/kharcha-tracker ]; then echo "ERROR: backup archive is corrupt" >&2; exit 1; fi
{save}
{guard}
if [ -d kharcha-tracker ]; then
  find kharcha-tracker -mindepth 1 -maxdepth 1 ! -name .venv ! -name cache -exec rm -rf {{}} +
  cp -a .restore-staging/kharcha-tracker/. kharcha-tracker/
else
  mv .restore-staging/kharcha-tracker kharcha-tracker
fi
{restore}
trap - EXIT
rm -rf .restore-staging
if [ -d kharcha-tracker/.venv ]; then
  cd kharcha-tracker
  . .venv/bin/activate
  python -m pip install -r requirement.txt
  cd ..
fi
echo "RESTORE_OK"
"""


def _remote_health_cmd(port: int) -> str:
    """python3 health probe: retries 15x1s; prints HEALTH_OK/HEALTH_FAIL."""
    script = (
        "import sys, time, urllib.request\n"
        f"url = \"http://127.0.0.1:{port}/api/v1/health\"\n"
        "for i in range(15):\n"
        "    try:\n"
        "        urllib.request.urlopen(url, timeout=2)\n"
        "        print(\"HEALTH_OK\")\n"
        "        sys.exit(0)\n"
        "    except Exception:\n"
        "        time.sleep(1)\n"
        "print(\"HEALTH_FAIL\")\n"
        "sys.exit(1)\n"
    )
    return f"python3 -c {shlex.quote(script)}"


def _remote_start_script(remote_path: str, storage: str, svc_port: int,
                         production: bool) -> str:
    """Start the service detached and gate on the health check
    (script rc != 0 when the service does not become healthy)."""
    reload_flag = "" if production else " --reload"
    app_dir = shlex.quote(f"{remote_path}/kharcha-tracker")
    return f"""set -e
cd {app_dir}
. .venv/bin/activate
export KHARCHA_STORAGE={shlex.quote(storage)}
nohup python -m uvicorn {RUN_MODULE} --host 0.0.0.0 --port {svc_port}{reload_flag} > server.log 2>&1 &
sleep 1
{_remote_health_cmd(svc_port)}
"""


# -- remote backup / restore actions ----------------------------------------
def remote_backup(session: SSHSession, remote_path: str) -> str:
    """Create a remote backup; returns the backup file name."""
    name = f"{BACKUP_PREFIX}{_backup_stamp()}.tar.gz"
    log(f"Backing up remote installation -> {remote_path}/backups/{name}")
    session.exec(_remote_backup_cmd(remote_path, name))
    session.exec(_remote_rotate_cmd(remote_path))
    log(f"Remote backup created: {name} (keeping {BACKUP_KEEP})")
    return name


def remote_latest_backup(session: SSHSession, remote_path: str) -> str | None:
    out, _err, rc = session.exec(_remote_latest_backup_cmd(remote_path),
                                 check=False)
    name = out.strip().splitlines()[0].strip() if out.strip() else ""
    if rc != 0 or not name:
        return None
    return Path(name).name


def remote_restore(session: SSHSession, remote_path: str, name: str,
                   svc_port: int, production: bool, storage: str,
                   restart: bool = True) -> None:
    """Restore the remote installation from *name*; restart the service
    if it was running (or *restart* is forced)."""
    log(f"Restoring remote installation from {name} ...")
    remote_detect_and_stop(session, svc_port)  # defensive: kill broken instance
    session.exec(_remote_restore_script(remote_path, name))
    log(f"Remote restore complete from {name}.")
    if restart:
        session.exec(
            _remote_start_script(remote_path, storage, svc_port, production),
            check=False,
        )


def remote_backup_action(user: str, host: str, ssh_port: int,
                         remote_path: str) -> None:
    """Standalone: create a backup of the remote installation."""
    session = SSHSession(host, ssh_port, user)
    session.connect()
    try:
        session.exec(f"mkdir -p {shlex.quote(remote_path)}")
        if not remote_dir_exists(session, f"{remote_path}/kharcha-tracker"):
            fail(f"No installation found at {remote_path}/kharcha-tracker "
                 f"on {host}; nothing to back up.", EXIT_VALIDATE_FAIL)
        remote_backup(session, remote_path)
    finally:
        session.close()


def remote_restore_action(user: str, host: str, ssh_port: int,
                          remote_path: str, svc_port: int,
                          production: bool, restart: bool) -> None:
    """Standalone: restore the remote installation from its latest backup."""
    session = SSHSession(host, ssh_port, user)
    session.connect()
    try:
        name = remote_latest_backup(session, remote_path)
        if not name:
            fail(f"No backups found in {remote_path}/backups on {host}. "
                 "Create one first with --backup.", EXIT_VALIDATE_FAIL)
        storage = os.environ.get("KHARCHA_STORAGE", "flat_file")
        remote_restore(session, remote_path, name, svc_port, production,
                       storage, restart=restart)
    finally:
        session.close()


def _remote_update_script(remote_path: str) -> str:
    """Extract the uploaded tarball over kharcha-tracker/ and install
    dependencies.

    The tree swap only replaces code: .venv/, cache/ and all database
    files are preserved (copied aside before deletion, copied back after;
    a trap covers dying mid-swap).
    """
    rp = shlex.quote(remote_path)
    save = _remote_preserve_cmd("save", remote_path)
    restore = _remote_preserve_cmd("restore", remote_path)
    guard = _remote_preserve_guard(remote_path)
    return f"""set -e
cd {rp}
rm -rf .kharcha-staging
mkdir -p .kharcha-staging
tar -xzf {TARBALL_NAME} -C .kharcha-staging
{save}
{guard}
if [ -d kharcha-tracker ]; then
  find kharcha-tracker -mindepth 1 -maxdepth 1 ! -name .venv ! -name cache -exec rm -rf {{}} +
  cp -a .kharcha-staging/kharcha-tracker/. kharcha-tracker/
else
  mv .kharcha-staging/kharcha-tracker kharcha-tracker
fi
rm -rf .kharcha-staging
{restore}
trap - EXIT
cd kharcha-tracker
if [ ! -d .venv ]; then
  PYTHON=python3
  command -v python3 >/dev/null 2>&1 || PYTHON=python
  $PYTHON -m venv .venv
fi
. .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirement.txt
echo "Remote update complete."
"""


def deploy_remote(user: str, host: str, ssh_port: int, remote_path: str,
                  start: bool, production: bool, svc_port: int) -> None:
    # 1. Build tarball into deployment/
    tarball = create_tarball()

    # 2. One connection for everything (password prompted at most once)
    session = SSHSession(host, ssh_port, user)
    session.connect()

    session.exec(f"mkdir -p {shlex.quote(remote_path)}")
    app_dir = f"{remote_path}/kharcha-tracker"
    storage = os.environ.get("KHARCHA_STORAGE", "flat_file")

    was_running = False
    backup_name: str | None = None

    try:
        # 3. Detect & stop the running service (target port + any stale
        #    kharcha instance on other ports), then back up what is there
        target_running = remote_detect_and_stop(session, svc_port)
        others_stopped = remote_stop_other_kharcha(session, svc_port)
        was_running = target_running or others_stopped > 0
        had_install = remote_dir_exists(session, app_dir)
        if had_install:
            backup_name = remote_backup(session, remote_path)
        else:
            log("No previous remote installation found; skipping backup.")

        # 4. Upload tarball
        session.put(tarball, f"{remote_path}/{TARBALL_NAME}")

        # 5. Extract and install dependencies (DB/cache preserved across
        #    the swap -- see _remote_update_script)
        update_script = _remote_update_script(remote_path)
        log("Running remote update (extract + venv + install) ...")
        session.exec(update_script)

        # 6. Start the service if it was running or --remote-start given;
        #    a failed health check here counts as an installation failure
        if start or was_running:
            log("Starting service on remote ...")
            _out, _err, rc = session.exec(
                _remote_start_script(remote_path, storage, svc_port, production),
                check=False,
            )
            if rc != 0:
                fail("Service failed its health check after deployment.",
                     EXIT_SERVICE_FAIL)
        else:
            log("Remote deployment done. Service not started "
                "(was not running; use --remote-start to start it).")

    except SystemExit as exc:
        # Installation failed -> restore the previous version from backup
        if exc.code in (0, None):
            raise
        if backup_name:
            log("Deployment failed -- restoring previous installation ...")
            try:
                remote_restore(session, remote_path, backup_name, svc_port,
                               production, storage, restart=was_running)
                log("Previous installation restored"
                    + (" and restarted." if was_running else "."))
            except SystemExit:
                print(
                    "[deploy] CRITICAL: automatic restore failed! Manual "
                    f"recovery: ssh to {remote_path} and extract "
                    f"backups/{backup_name}.",
                    file=sys.stderr,
                )
        else:
            log("No previous installation to restore (first deployment "
                "or backup unavailable).")
        session.close()
        raise

    session.close()


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
def _default_port() -> int:
    try:
        return int(os.environ.get("KHARCHA_PORT", "8000"))
    except ValueError:
        return 8000


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Deploy kharcha_v1/kharcha_gateway.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--production", action="store_true",
        help="Start without --reload (local or remote).",
    )
    parser.add_argument(
        "--offline", action="store_true",
        help="Prefer local cache; fall back to PyPI.",
    )
    parser.add_argument(
        "--tarball", action="store_true",
        help="Build tarball only (into deployment/) and exit.",
    )
    parser.add_argument(
        "--backup", action="store_true",
        help="Back up the current installation and exit "
             "(remote with --remote, otherwise local).",
    )
    parser.add_argument(
        "--restore", action="store_true",
        help="Restore the most recent backup and exit "
             "(remote with --remote, otherwise local).",
    )
    parser.add_argument(
        "--remote", metavar="USER@HOST[:PORT]",
        help="Deploy to a remote server over SSH (password prompted once).",
    )
    parser.add_argument(
        "--remote-path", default=os.environ.get("REMOTE_PATH", DEFAULT_REMOTE_PATH),
        help=f"Remote deployment path (default: {DEFAULT_REMOTE_PATH}).",
    )
    parser.add_argument(
        "--remote-start", action="store_true",
        help="Start the service on the remote server after deployment.",
    )
    parser.add_argument(
        "--no-start", action="store_true",
        help="Skip stopping/starting the service locally.",
    )
    parser.add_argument(
        "--storage", default=os.environ.get("KHARCHA_STORAGE", "flat_file"),
        choices=["flat_file", "sql_lite", "postgres"],
        help="Storage backend (sets KHARCHA_STORAGE).",
    )
    parser.add_argument(
        "--port", type=int, default=_default_port(),
        help="Service port (default: 8000).",
    )
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()

    if args.backup and args.restore:
        fail("--backup and --restore are mutually exclusive.", EXIT_BAD_ARGS)

    os.environ["KHARCHA_STORAGE"] = args.storage
    os.environ["KHARCHA_PORT"] = str(args.port)
    if args.no_start:
        os.environ["KHARCHA_NO_START"] = "1"

    # --tarball only (works even with a broken installation)
    if args.tarball:
        output = create_tarball()
        log(f"Done. Tarball at: {output}")
        sys.exit(EXIT_OK)

    # --backup / --restore (standalone; must run BEFORE source validation
    # so they still work when the installation is broken)
    if args.backup:
        if args.remote:
            user, host, ssh_port = parse_remote(args.remote)
            remote_backup_action(user, host, ssh_port, args.remote_path)
        else:
            local_backup()
        sys.exit(EXIT_OK)

    if args.restore:
        if args.remote:
            user, host, ssh_port = parse_remote(args.remote)
            remote_restore_action(user, host, ssh_port, args.remote_path,
                                  svc_port=args.port,
                                  production=args.production,
                                  restart=not args.no_start)
        else:
            local_restore_latest()
        sys.exit(EXIT_OK)

    # Validate source (deploy modes only)
    if not KHARCHA_V1_DIR.exists():
        fail(f"Deployment source not found: {KHARCHA_V1_DIR}", EXIT_BAD_ARGS)
    if not REQUIREMENTS_FILE.exists():
        fail(f"Requirements file not found: {REQUIREMENTS_FILE}", EXIT_BAD_ARGS)

    # Remote mode: stop -> backup -> update -> (re)start, single password prompt
    if args.remote:
        user, host, ssh_port = parse_remote(args.remote)
        deploy_remote(
            user, host, ssh_port,
            remote_path=args.remote_path,
            start=args.remote_start,
            production=args.production,
            svc_port=args.port,
        )
        sys.exit(EXIT_OK)

    # Local mode: stop running service(s) -> backup -> update -> start again
    python = find_python()

    was_running = False
    if not args.no_start:
        target_running = detect_and_stop_service(args.port)
        others_stopped = detect_and_stop_other_ports(args.port)
        was_running = target_running or others_stopped > 0

    # Snapshot the current installation before touching it (last 3 kept)
    backup_path: Path | None = None
    if PROJECT_ROOT.exists():
        backup_path = local_backup()
    else:
        log("No existing installation to back up (first deploy).")

    try:
        create_venv(python)
        upgrade_pip()
        install_dependencies(offline=args.offline)

        if args.no_start:
            log("Deployment complete (--no-start; service not touched).")
            sys.exit(EXIT_OK)

        if was_running:
            log("Update complete; restarting service ...")
        start_local(production=args.production, port=args.port)
    except SystemExit as exc:
        # Update/start failed -> roll back to the pre-deploy snapshot
        if exc.code in (0, None):
            raise
        if backup_path is None:
            log("Deployment failed and no backup exists; nothing to restore.")
            raise
        log("Deployment failed -- restoring previous installation ...")
        try:
            local_restore(backup_path)
            if was_running and not args.no_start:
                start_local_detached(args.port)
            log("Previous installation restored"
                + (" and restarted." if was_running else "."))
        except SystemExit:
            print(
                "[deploy] CRITICAL: automatic restore failed! Manual "
                f"recovery: extract {backup_path} over {PROJECT_ROOT.parent}/ "
                "(keeping .venv).",
                file=sys.stderr,
            )
        raise


if __name__ == "__main__":
    main()
