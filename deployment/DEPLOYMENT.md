# Kharcha Tracker — Deployment Guide

Deploy the `kharcha_v1/kharcha_gateway` FastAPI application into a Python
virtual environment. Supports local, offline, and remote (SSH) deployment,
with automatic backup before every update and automatic rollback when an
update fails.

## Prerequisites

- Python 3.10+ (3.11+ recommended)
- `paramiko` for remote deployment (install once:
  `python3 -m pip install paramiko "cryptography<45"`)
- Network access to PyPI (or a populated offline cache)

## Quick Start

```bash
# Local development (creates kharcha-tracker/.venv, installs deps,
# stops any service already on the port, then starts with --reload)
python deployment/deploy.py

# Local production (starts without --reload)
python deployment/deploy.py --production
```

## Modes

### Local Deployment

```bash
python deployment/deploy.py                  # dev mode, --reload enabled
python deployment/deploy.py --production     # production mode
python deployment/deploy.py --no-start       # install only, do not touch service
python deployment/deploy.py --port 9000      # custom port
python deployment/deploy.py --storage sql_lite
```

Creates the virtual environment at `kharcha-tracker/.venv`, installs from
`requirement.txt`, and starts uvicorn bound to `0.0.0.0:8000`.

**Stop / update / restart**: before updating, the script checks for
running kharcha services — **on the target port and on any other port** —
and stops them (SIGTERM, then SIGKILL after 10s). This guarantees the
deployed port is the only instance serving afterwards; a stale instance
from an earlier default-port deploy (e.g. 8000) cannot keep serving old
code. Only processes whose command mentions `kharcha` are stopped;
unrelated processes on the target port are left alone (a warning is
logged). The current installation is then backed up, dependencies are
updated, and the service is started on the requested `--port`.

**Automatic rollback**: if the update or the service health check fails,
the script restores the pre-deploy backup (and restarts the service if it
was running before) and exits with the original failure code.

### Tarball Build

```bash
python deployment/deploy.py --tarball
# or
make -f deployment/Makefile tarball
```

Produces `deployment/kharcha_gateway.tar.gz` containing only the
deployment folder contents:

```
kharcha-tracker/
|-- kharcha_v1/...         # application tree (gateway, services, ...)
+-- requirement.txt        # dependencies
```

Excludes `__pycache__/`, `.DS_Store`, `.pytest_cache/`, `.git/`, `.venv/`.

### Offline Deployment

```bash
python deployment/deploy.py --offline
```

1. Attempts PyPI install.
2. On failure, installs from `cache/pip-packages/`.
3. If cache is empty, attempts `pip download` to populate it.
4. Fails with a clear message if neither source is available.

#### Populating the Cache

On a machine with internet access:

```bash
make -f deployment/Makefile cache
# or
python -m pip download -r requirement.txt -d cache/pip-packages
```

Copy the `cache/` directory to the offline machine before deploying.

### Remote Deployment (SSH)

```bash
python deployment/deploy.py --remote user@server.example.com
python deployment/deploy.py --remote deploy@192.168.1.10:2222 --remote-path /opt/kharcha
python deployment/deploy.py --remote deploy@host --remote-start --production
```

Or via make:

```bash
make -f deployment/Makefile remote REMOTE=deploy@192.168.1.10
make -f deployment/Makefile remote-start REMOTE=deploy@192.168.1.10 REMOTE_PATH=/opt/kharcha
```

Steps performed on the remote server:

1. Create remote directory
2. Detect and stop running kharcha instances — on the target port
   (any uvicorn/kharcha process) **and on every other port** (stale
   instances, typically port 8000 from an earlier deploy; only processes
   whose command mentions `kharcha`)
3. Back up the existing `kharcha-tracker/` (code only: no `.venv`, no
   database files) into `<remote-path>/backups/` (newest 3 kept)
4. Upload tarball over the same SSH connection (SFTP)
5. Extract and replace the code files — `.venv/`, `cache/` and all
   database files are preserved across the swap
6. Create `.venv` with system Python if missing
7. Install dependencies from `requirement.txt`
8. Start the service on the `--port` value if any instance was running
   before (any port) or if `--remote-start` is given
9. Health check on `/api/v1/health` at the target port (15 × 1s retries)

**Port semantics**: `--port N` means *the* service port after deploy —
exactly one kharcha instance, listening on N. If a previous instance ran
on 8000 and you deploy with `--port 9000`, the 8000 instance is stopped
and the new one starts on 9000 (no `--remote-start` needed when a
previous instance existed).

If any step from 4 onward fails (including the health check), the script
restores the backup from step 3, restarts the service if it was running
before the deploy, and exits with the original failure code.

**One password, asked once**: if key/agent authentication does not work,
the script prompts for the SSH password exactly once (input is not echoed)
and keeps it only in memory for the lifetime of the process. It is never
written to disk and never passed as a command-line argument. All subsequent
commands and the file upload reuse the same SSH connection.

For key-based authentication, set `SSH_KEY_PATH`:

```bash
export SSH_KEY_PATH=~/.ssh/deploy_key
python deployment/deploy.py --remote user@host
```

## Backup & Restore

Every deployment first snapshots the current installation, and rolls back
to that snapshot automatically if the update or the health check fails.

### Automatic (during deploy)

```
stop service -> backup -> upload/update -> start + health check
                     ^                            |
                     +---- on failure: restore <--+
```

- **Local backups** go to `deployment/backups/kharcha-tracker-<timestamp>.tar.gz`
- **Remote backups** go to `<remote-path>/backups/` on the server
- Newest **3** backups are kept (older ones rotate out)
- A backup contains **code files only**: no `.venv/`, no `cache/`, and
  **no database files** (`flat_file_db/`, `*.db`, `*.sqlite*`)
- On rollback the existing `.venv` is kept and dependencies are re-synced
  with the restored `requirement.txt`

### Database safety

Database and runtime-data files are **never touched** by any deployment
operation — not by tarball creation, not by backups, not by updates, not
by restores:

- Tarballs and backups exclude `flat_file_db/` and `*.db` / `*.sqlite*`
  files, so no archive ever ships or reintroduces database content.
- Before any tree swap (update or restore), database files are **copied
  aside** to `<parent>/.kharcha-preserve/`; after the swap they are
  **copied back**. The originals are never deleted before the copy exists,
  and archive content can never overwrite them.
- If a script dies mid-swap (e.g. lost SSH connection), a shell `trap`
  copies the preserved files back before exit; the preserve directory is
  also the recovery location if a connection dies at the worst possible
  moment (copy its contents back into `kharcha-tracker/` manually).
- `kharcha-tracker/cache/` (offline package cache) is likewise kept
  across swaps.
- Local deployment (`pip` install into `.venv`) never modifies the
  application tree at all.

### Manual

```bash
python deployment/deploy.py --backup              # local snapshot
python deployment/deploy.py --restore             # roll back to latest local snapshot
python deployment/deploy.py --remote user@host --backup    # remote snapshot
python deployment/deploy.py --remote user@host --restore   # roll back remote
```

Or via make:

```bash
make -f deployment/Makefile backup
make -f deployment/Makefile restore
make -f deployment/Makefile remote-backup REMOTE=user@host
make -f deployment/Makefile remote-restore REMOTE=user@host
```

`--restore` stops the service if it is running, restores the latest
backup, and starts the service again if it had been running (skip both
with `--no-start`). The same backup/restore flags work even when the
installation is broken.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `KHARCHA_STORAGE` | Storage backend (`flat_file`, `sql_lite`, `postgres`) | `flat_file` |
| `KHARCHA_PORT` | Service listen port | `8000` |
| `PYTHON_VERSION` | Python interpreter to use for venv | `python3` |
| `SSH_KEY_PATH` | SSH private key for remote deploy | agent/default |
| `REMOTE_PATH` | Remote deployment path | `/var/www/kharcha-tracker` |

## CLI Reference

```
python deployment/deploy.py [--production] [--offline] [--tarball]
                 [--backup | --restore]
                 [--remote USER@HOST[:PORT]] [--remote-path PATH]
                 [--remote-start] [--no-start]
                 [--storage {flat_file,sql_lite,postgres}] [--port PORT]
```

| Flag | Effect |
|------|--------|
| `--production` | Start without `--reload` |
| `--offline` | Prefer local cache over PyPI |
| `--tarball` | Build tarball only, then exit |
| `--backup` | Back up the current installation, then exit |
| `--restore` | Restore the latest backup, then exit |
| `--remote` | Deploy to remote server over SSH |
| `--remote-path` | Remote installation directory |
| `--remote-start` | Start service on remote after deploy |
| `--no-start` | Install dependencies but do not start |
| `--storage` | Set `KHARCHA_STORAGE` backend |
| `--port` | Service port: stops kharcha on this AND all other ports, starts here |

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Unknown error |
| 2 | Invalid arguments |
| 3 | Virtual environment creation failed |
| 4 | Dependency installation failed |
| 5 | Offline mode: packages not available |
| 6 | Remote SSH connection failed |
| 7 | Remote deployment failed |
| 8 | Service startup failed |
| 9 | Validation failed |

## Makefile Targets

| Target | Description |
|--------|-------------|
| `make -f deployment/Makefile local` | Local dev deploy + start |
| `make -f deployment/Makefile production` | Local production deploy + start |
| `make -f deployment/Makefile offline` | Offline-mode deploy |
| `make -f deployment/Makefile tarball` | Build tarball only |
| `make -f deployment/Makefile remote REMOTE=user@host` | Remote deploy |
| `make -f deployment/Makefile remote-start REMOTE=user@host` | Remote deploy + start |
| `make -f deployment/Makefile backup` | Back up the local installation |
| `make -f deployment/Makefile restore` | Restore local installation from latest backup |
| `make -f deployment/Makefile remote-backup REMOTE=user@host` | Back up the remote installation |
| `make -f deployment/Makefile remote-restore REMOTE=user@host` | Restore remote installation |
| `make -f deployment/Makefile cache` | Populate offline cache from PyPI |
| `make -f deployment/Makefile clean` | Remove `.venv`, tarball, cache (keeps backups) |

Any deploy/restore target accepts the service port via `PORT=`
(default 8000):

```bash
make -f deployment/Makefile local PORT=9000
make -f deployment/Makefile remote REMOTE=deploy@host PORT=9000
make -f deployment/Makefile remote-start REMOTE=deploy@host PORT=9000
```

## Remote Server Requirements

- Python 3.10+ available as `python3` (or `python`)
- OpenSSH server running, with password or key authentication enabled
- Write access to the deployment path
- Port 8000 (or chosen port) free, or open for the service

## Troubleshooting

**"No suitable Python found"**
Install Python 3.11+ or set `PYTHON_VERSION` to the correct interpreter.

**"Offline deployment failed"**
Populate `cache/pip-packages/` on a connected machine:
```bash
python -m pip download -r requirement.txt -d cache/pip-packages
```
Then copy `cache/` to the offline machine.

**SSH connection fails**
- Verify the key: `ssh -i $SSH_KEY_PATH user@host`
- Check network connectivity and firewall rules
- The script retries 3 times with exponential backoff before failing.
- Password authentication: the prompt appears at most once per run; if it
  is cancelled or empty, the script fails with an error instead of asking
  again.

**"Remote deployment requires paramiko"**
```bash
python3 -m pip install paramiko "cryptography<45"
```

**Service won't start on remote**
SSH to the server, activate the venv, and run uvicorn manually to see logs:
```bash
cd /var/www/kharcha-tracker/kharcha-tracker
. .venv/bin/activate
export KHARCHA_STORAGE=flat_file
python -m uvicorn kharcha_v1.kharcha_gateway.app.main:app --host 0.0.0.0 --port 8000
```

**Port already in use / old instance still answering**
The deploy script stops kharcha/uvicorn services on the target port AND
any kharcha instance on other ports before updating, so exactly one
instance remains — on `--port`. Make targets accept the port via
`PORT=`: `make -f deployment/Makefile local PORT=9000`. For an unrelated
process occupying the port, stop it yourself or use a different port:
```bash
lsof -i :8000
python deployment/deploy.py --port 8001
```

**Deployment interrupted mid-swap (connection lost)**
Database files are never lost: before any tree swap they are copied to
`<deployment-path>/.kharcha-preserve/`. A shell `trap` restores them on
script exit; only if the connection died at that exact moment would the
directory remain. Copy the contents back manually:
```bash
cp -a /var/www/kharcha-tracker/.kharcha-preserve/. /var/www/kharcha-tracker/kharcha-tracker/
rm -rf /var/www/kharcha-tracker/.kharcha-preserve
```
