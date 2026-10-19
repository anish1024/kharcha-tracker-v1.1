#!/usr/bin/env python3
"""Kharcha Tracker deployment script.

Supports local, offline, and remote (SSH) deployment of
kharcha_v1/kharcha_gateway into a Python virtual environment.

Usage (run from the repository root):
    python deployment/deploy.py                          # local install + start
    python deployment/deploy.py --production             # local production start
    python deployment/deploy.py --offline                # install from cache if PyPI fails
    python deployment/deploy.py --tarball                # build tarball only
    python deployment/deploy.py --remote user@host       # full remote deploy
    python deployment/deploy.py --remote user@host --remote-path /opt/kharcha
    python deployment/deploy.py --remote user@host:2222 --remote-start
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
from pathlib import Path

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
PROJECT_ROOT = REPO_ROOT / "kharcha-tracker"
KHARCHA_DIR = PROJECT_ROOT / "kharcha_v1" / "kharcha_gateway"
REQUIREMENTS_FILE = PROJECT_ROOT / "requirement.txt"
VENV_DIR = PROJECT_ROOT / ".venv"
CACHE_DIR = PROJECT_ROOT / "cache" / "pip-packages"
TARBALL_NAME = "kharcha_gateway.tar.gz"
DEFAULT_REMOTE_PATH = "/var/www/kharcha-tracker"
RUN_MODULE = "kharcha_v1.kharcha_gateway.app.main:app"

EXCLUDE_PATTERNS = {"__pycache__", ".DS_Store", ".pytest_cache", ".git", ".venv", "venv"}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def log(msg: str) -> None:
    print(f"[deploy] {msg}")


def fail(msg: str, code: int = EXIT_UNKNOWN) -> None:
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
    """Return path to an executable inside the venv."""
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
# Tarball
# ---------------------------------------------------------------------------
def _filter_tarinfo(tarinfo: tarfile.TarInfo) -> tarfile.TarInfo | None:
    parts = Path(tarinfo.name).parts
    if any(part in EXCLUDE_PATTERNS for part in parts):
        return None
    tarinfo.uid = tarinfo.gid = 0
    tarinfo.uname = tarinfo.gname = "root"
    return tarinfo


def create_tarball(output: Path) -> Path:
    if not KHARCHA_DIR.exists():
        fail(f"Source directory not found: {KHARCHA_DIR}", EXIT_BAD_ARGS)
    log(f"Creating tarball: {output}")
    # Include requirement.txt alongside the gateway tree
    with tarfile.open(output, "w:gz") as tar:
        tar.add(str(KHARCHA_DIR), arcname="kharcha_gateway", filter=_filter_tarinfo)
        tar.add(str(REQUIREMENTS_FILE), arcname="requirement.txt")
    size_kb = output.stat().st_size / 1024
    log(f"Tarball created ({size_kb:.1f} KB): {output}")
    return output


# ---------------------------------------------------------------------------
# Local service start
# ---------------------------------------------------------------------------
def start_local(production: bool) -> None:
    python_bin = str(venv_bin("python"))
    cmd = [
        python_bin, "-m", "uvicorn", RUN_MODULE,
        "--host", "0.0.0.0",
        "--port", os.environ.get("KHARCHA_PORT", "8000"),
    ]
    if not production:
        cmd.append("--reload")
    storage = os.environ.get("KHARCHA_STORAGE", "flat_file")
    env = {"KHARCHA_STORAGE": storage}
    log(f"Starting service (storage={storage}) ...")
    log(f"  Run target : {RUN_MODULE}")
    log(f"  URL        : http://0.0.0.0:{os.environ.get('KHARCHA_PORT', '8000')}")
    try:
        run(cmd, cwd=PROJECT_ROOT, env=env)
    except KeyboardInterrupt:
        log("Service stopped.")
    except Exception:
        fail("Service startup failed.", EXIT_SERVICE_FAIL)


# ---------------------------------------------------------------------------
# Remote (SSH) deployment — uses system ssh/scp, no paramiko dependency
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


def ssh_cmd(user: str, host: str, port: int) -> list[str]:
    cmd = ["ssh", "-p", str(port), "-o", "StrictHostKeyChecking=accept-new"]
    key = os.environ.get("SSH_KEY_PATH")
    if key:
        cmd += ["-i", key]
    if user:
        cmd.append(f"{user}@{host}")
    else:
        cmd.append(host)
    return cmd


def scp_cmd(user: str, host: str, port: int) -> list[str]:
    cmd = ["scp", "-P", str(port), "-o", "StrictHostKeyChecking=accept-new"]
    key = os.environ.get("SSH_KEY_PATH")
    if key:
        cmd += ["-i", key]
    return cmd


def remote_exec(user: str, host: str, port: int, remote_script: str,
                retries: int = 3) -> None:
    base = ssh_cmd(user, host, port)
    for attempt in range(1, retries + 1):
        result = subprocess.run(
            [*base, remote_script],
            capture_output=True, text=True,
        )
        if result.stdout:
            print(result.stdout)
        if result.returncode == 0:
            return
        if result.stderr:
            print(result.stderr, file=sys.stderr)
        if attempt < retries:
            wait = 2 ** attempt
            log(f"SSH attempt {attempt}/{retries} failed. Retrying in {wait}s ...")
            time.sleep(wait)
    fail(f"SSH command failed after {retries} attempts.", EXIT_SSH_FAIL)


def deploy_remote(user: str, host: str, port: int, remote_path: str,
                  start: bool, production: bool) -> None:
    # 1. Build tarball locally
    with tempfile.TemporaryDirectory() as tmpdir:
        tarball = Path(tmpdir) / TARBALL_NAME
        create_tarball(tarball)

        # 2. Ensure remote directory exists
        remote_exec(user, host, port, f"mkdir -p {remote_path}")

        # 3. Copy tarball to remote
        dest = f"{user}@{host}:{remote_path}/{TARBALL_NAME}" if user \
            else f"{host}:{remote_path}/{TARBALL_NAME}"
        copy_cmd = [
            *scp_cmd(user, host, port),
            str(tarball), dest,
        ]
        log(f"Copying tarball to {dest} ...")
        result = subprocess.run(copy_cmd, capture_output=True, text=True)
        if result.returncode != 0:
            fail(f"scp failed:\n{result.stderr}", EXIT_REMOTE_DEPLOY_FAIL)

    # 4. Extract, create venv, install deps on remote
    remote_script = f"""
set -e
cd {remote_path}
tar -xzf {TARBALL_NAME}
cd kharcha_gateway

# Create parent package structure expected by the run target
if [ ! -d kharcha_v1 ]; then
    mkdir -p kharcha_v1
    cp -r kharcha_gateway/* kharcha_v1/ 2>/dev/null || true
fi

PYTHON=python3
command -v python3 >/dev/null 2>&1 || PYTHON=python

$PYTHON -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirement.txt

echo "Remote deployment complete."
"""
    log("Running remote deployment (extract + venv + install) ...")
    remote_exec(user, host, port, remote_script)

    if start:
        storage = os.environ.get("KHARCHA_STORAGE", "flat_file")
        host_flag = "0.0.0.0"
        port_flag = os.environ.get("KHARCHA_PORT", "8000")
        reload_flag = "" if production else " --reload"
        start_script = f"""
cd {remote_path}/kharcha_gateway
. .venv/bin/activate
export KHARCHA_STORAGE={storage}
nohup python -m uvicorn {RUN_MODULE} \
    --host {host_flag} --port {port_flag}{reload_flag} \
    > server.log 2>&1 &
echo "Service started (PID $!). Log: {remote_path}/kharcha_gateway/server.log"
"""
        log("Starting service on remote ...")
        remote_exec(user, host, port, start_script)
    else:
        log("Remote deployment done. Service not started (use --remote-start).")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
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
        help="Build tarball only and exit.",
    )
    parser.add_argument(
        "--remote", metavar="USER@HOST[:PORT]",
        help="Deploy to a remote server over SSH.",
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
        help="Skip starting the service locally.",
    )
    parser.add_argument(
        "--storage", default=os.environ.get("KHARCHA_STORAGE", "flat_file"),
        choices=["flat_file", "sql_lite", "postgres"],
        help="Storage backend (sets KHARCHA_STORAGE).",
    )
    parser.add_argument(
        "--port", default=os.environ.get("KHARCHA_PORT", "8000"),
        help="Service port (default: 8000).",
    )
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()

    # Validate source directory
    if not KHARCHA_DIR.exists():
        fail(f"Deployment source not found: {KHARCHA_DIR}", EXIT_BAD_ARGS)
    if not REQUIREMENTS_FILE.exists():
        fail(f"Requirements file not found: {REQUIREMENTS_FILE}", EXIT_BAD_ARGS)

    os.environ["KHARCHA_STORAGE"] = args.storage
    os.environ["KHARCHA_PORT"] = args.port

    # --tarball only
    if args.tarball:
        output = PROJECT_ROOT / TARBALL_NAME
        create_tarball(output)
        log(f"Done. Tarball at: {output}")
        sys.exit(EXIT_OK)

    # Remote mode
    if args.remote:
        user, host, port = parse_remote(args.remote)
        deploy_remote(
            user, host, port,
            remote_path=args.remote_path,
            start=args.remote_start,
            production=args.production,
        )
        sys.exit(EXIT_OK)

    # Local mode
    python = find_python()
    create_venv(python)
    upgrade_pip()
    install_dependencies(offline=args.offline)

    if args.no_start:
        log("Deployment complete (--no-start).")
        sys.exit(EXIT_OK)

    start_local(production=args.production)


if __name__ == "__main__":
    main()
