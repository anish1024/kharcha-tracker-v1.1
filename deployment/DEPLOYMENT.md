# Kharcha Tracker — Deployment Guide

Deploy the `kharcha_v1/kharcha_gateway` FastAPI application into a Python
virtual environment. Supports local, offline, and remote (SSH) deployment.

## Prerequisites

- Python 3.10+ (3.11+ recommended)
- OpenSSH client (for remote deployment)
- Network access to PyPI (or a populated offline cache)

## Quick Start

```bash
# Local development (creates .venv, installs deps, starts with --reload)
python deployment/deploy.py

# Local production (starts without --reload)
python deployment/deploy.py --production
```

## Modes

### Local Deployment

```bash
python deployment/deploy.py                  # dev mode, --reload enabled
python deployment/deploy.py --production     # production mode
python deployment/deploy.py --no-start       # install only, do not start service
python deployment/deploy.py --port 9000      # custom port
python deployment/deploy.py --storage sql_lite
```

Creates `.venv` in the project root, installs from `requirement.txt`, and
starts uvicorn bound to `0.0.0.0:8000`.

### Tarball Build

```bash
python deployment/deploy.py --tarball
# or
make -f deployment/Makefile tarball
```

Produces `kharcha-tracker/kharcha_gateway.tar.gz` containing:

```
kharcha_gateway/          # the full gateway tree
requirement.txt
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
2. Copy tarball via `scp`
3. Extract tarball
4. Create `.venv` with system Python
5. Install dependencies from `requirement.txt`
6. Optionally start the service (`--remote-start`)

Requires SSH key-based authentication. Set `SSH_KEY_PATH` if using a
non-default key:

```bash
export SSH_KEY_PATH=~/.ssh/deploy_key
python deployment/deploy.py --remote user@host
```

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
                 [--remote USER@HOST[:PORT]] [--remote-path PATH]
                 [--remote-start] [--no-start]
                 [--storage {flat_file,sql_lite,postgres}] [--port PORT]
```

| Flag | Effect |
|------|--------|
| `--production` | Start without `--reload` |
| `--offline` | Prefer local cache over PyPI |
| `--tarball` | Build tarball only, then exit |
| `--remote` | Deploy to remote server over SSH |
| `--remote-path` | Remote installation directory |
| `--remote-start` | Start service on remote after deploy |
| `--no-start` | Install dependencies but do not start |
| `--storage` | Set `KHARCHA_STORAGE` backend |
| `--port` | Service listen port |

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
| `make -f deployment/Makefile cache` | Populate offline cache from PyPI |
| `make -f deployment/Makefile clean` | Remove `.venv`, tarball, cache |

## Remote Server Requirements

- Python 3.10+ available as `python3` (or `python`)
- OpenSSH server running
- SSH key-based auth configured for the deploy user
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

**Service won't start on remote**
SSH to the server, activate the venv, and run uvicorn manually to see logs:
```bash
cd /var/www/kharcha-tracker/kharcha_gateway
. .venv/bin/activate
export KHARCHA_STORAGE=flat_file
python -m uvicorn kharcha_v1.kharcha_gateway.app.main:app --host 0.0.0.0 --port 8000
```

**Port already in use**
```bash
lsof -i :8000
# kill the process or use a different port
python deployment/deploy.py --port 8001
```
