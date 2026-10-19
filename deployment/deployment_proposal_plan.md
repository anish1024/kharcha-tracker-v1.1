# Deployment Proposal: Kharcha Tracker v1.1

## Overview

This proposal covers deployment of the `kharcha_v1/kharcha_gateway` directory with support for:
- Local development via virtual environment
- Tarball deployment
- Remote SSH deployment to any server
- Offline installation mode for disconnected environments

## Deployment Options

### Option A: Single Comprehensive Script (`deploy.sh`)
One script that handles all modes:
- `./deploy.sh` — local install
- `./deploy.sh --offline` — offline mode
- `./deploy.sh --remote <ssh_user>@<host>[:port]` — SSH deploy

### Option B: Separate Scripts
- `deploy_local.sh` — local venv install
- `deploy_remote.sh` — SSH deploy

### Option C: Python Deployment Script (`deploy.py`)
Single entry point with argparse for mode selection (recommended)

**Recommended: Option C** — most maintainable, handles edge cases better

---

## Feature Specifications

### 1. Virtual Environment Setup
- Auto-create venv in project root (`.venv`)
- Python 3.11+ required
- Isolate dependencies from system Python

### 2. Tarball Deployment
- Package target folder into `kharcha_gateway.tar.gz`
- Include: app/, kharcha_service/, requirements.txt
- Exclude: `__pycache__/`, `.DS_Store`, `.pytest_cache/`
- Create tarball in `/tmp` before deploy

### 3. SSH Deployment
- Connect to remote server via SSH
- Transfer tarball using `scp` or `rsync`
- Create/activate venv on remote
- Install requirements on remote
- Optionally start service on remote

### 4. Offline Mode (Python Package Installation)
- Read `requirements.txt`
- Parse package names
- Attempt pip install from PyPI
- If PyPI unreachable, check `cache/` directory with same package names
- If offline cache exists, install from there
- Exit with clear error if both offline and online fail

### 5. Storage Backend Selection
Support via environment variable:
```bash
export KHARCHA_STORAGE=flat_file|sql_lite|postgres
```

### 6. Service Start
```bash
uvicorn kharcha_v1.kharcha_gateway.app.main:app \
  --host 0.0.0.0 \
  --port 8000
```

---

## Implementation Structure

```
deploy.py
├── parse_args()
│   ├── detect --offline flag
│   ├── detect --remote flag and parse host/user/port
│   └── default: local mode
│
├── prepare_environment(env=Env.LOCAL)
│   ├── if LOCAL: create_venv()
│   │   └── python -m venv .venv
│   ├── install_dependencies()
│   │   ├── ensure_offline_mode()
│   │   │   ├── if --offline:
│   │   │   │   ├── try_pypi()  # may fail
│   │   │   │   ├── if pypi fail: try_cache()
│   │   │   │   │   ├── if cache miss: download to cache
│   │   │   │   │   └── install from cache
│   │   │   │   └── if both fail: exit with error
│   │   │   └── else: install from PyPI (retry with backoff)
│   │   └── install_from_pypi()
│   │       ├── pip install -r requirements.txt
│   │       └── catch network errors
│   └── if REMOTE:
│       ├── deploy_on_remote()
│       │   ├── create_remote_venv()
│       │   └── install_on_remote()
│       └── if --remote-deploy all: start_service_on_remote()
│   └── setup_storage_backend()
│       └── validate KHARCHA_STORAGE env var
│
├── build_artifact(mode=ArtifactMode.TARBALL)
│   ├── if TARBALL: create_tarball()
│   │   ├── create_temp_dir()
│   │   ├── add_files(exclude_patterns)
│   │   ├── add_tarball_to_deploy_dir()
│   │   └── tar -czf output.tar.gz
│   └── if LOCAL: skip
│
├── deploy_to_remote()
│   ├── scp_artifact()
│   │   ├── scp -r kharcha_v1/ user@host:/path/
│   │   └── handle SCP failures
│   └── if --remote:
│       └── deploy_on_remote()  # same as above
│
├── start_service()
│   └── uvicorn kharcha_v1.kharcha_gateway.app.main:app --host 0.0.0.0 --port 8000
│
└── validate_deployment()
    ├── Check port 8000 is listening
    └── Optional: curl test to /api/v1/health
```

---

## File Outputs

### 1. `deploy.py` — Main deployment script
- CLI with argparse
- All deployment logic
- Handles local/remote/offline modes

### 2. `cache/` — Directory for offline Python packages
- Structure: `cache/pip-virtualenv/<hash>/` or `cache/pip-packages/<package>/`
- Subdirectory per package: `cache/pip-packages/<package_name>/<version>/`
- Auto-populated on first PyPI download if offline mode requested

### 3. `Makefile` (optional) — Convenience commands
```makefile
.PHONY: local offline remote build clean

local:
	python deploy.py

offline:
	python deploy.py --offline

remote:
	python deploy.py --remote user@host

build:
	tar -czf kharcha_gateway.tar.gz kharcha_v1/kharcha_gateway

clean:
	rm -rf .venv kharcha_gateway.tar.gz cache/
```

### 4. `DEPLOYMENT.md` — Documentation
- Usage instructions
- Environment variables
- Remote server requirements
- Troubleshooting guide

---

## Key Decisions to Confirm

### 1. Script Type
**Option C** (`deploy.py`):
- Single entry point with argparse
- Better error handling with Python exceptions
- Easier to test and extend
- Cross-platform (works on Windows, Linux, macOS)
- **Recommendation: Use this**

### 2. Remote Deploy Scope
| Option | Description | Recommended? |
|--------|-------------|--------------|
| A | Just copy files via SCP | No - doesn't deploy dependencies |
| B | Full deploy (copy + venv + install + start) | Yes - complete solution |

### 3. Offline Cache Management
| Option | Description | Recommended? |
|--------|-------------|--------------|
| A | Auto-download PyPI to cache on first offline run | Yes - convenient |
| B | Manual cache management only | No - user burden |

### 4. Remote Virtual Environment
| Option | Description | Recommended? |
|--------|-------------|--------------|
| A | Use system Python, create remote venv | Yes - uses server resources |
| B | Bundle venv in tarball | No - bloats artifact |

### 5. Include Frontend in Tarball
| Option | Description | Recommended? |
|--------|-------------|-------------- |
| A | Yes - include frontend_service/ | No - frontend deploys separately |
| B | No - only API Gateway | Yes - clearer separation |

### 6. Artifact Location
- Tarball output: `/tmp/kharcha_gateway.tar.gz`
- Deploy path: configurable via `--remote-path`
- Default remote path: `/var/www/kharcha-tracker/`

---

## Technical Implementation Details

### Virtual Environment Creation
```python
import venv

def create_venv():
    venv_path = Path(__file__).parent / ".venv"
    if (venv_path / "pyvenv.cfg").exists():
        print("Venv already exists")
        return
    venv.create(venv_path, with_pip=True)
```

### Offline Cache Download
```python
import subprocess
import os

def download_to_cache(package_name: str):
    cache_path = f"cache/pip-packages/{package_name}/"
    os.makedirs(cache_path, exist_ok=True)
    
    # Download wheel
    url = f"https://pypi.org/packages/{package_name}/<version>.whl"
    wheel_path = Path(cache_path) / f"{package_name}-<version>.whl"
    subprocess.run(["curl", "-L", url, "-o", str(wheel_path)])
```

### SSH Connection
```python
import paramiko

def ssh_deploy(host: str, user: str, port: int = 22):
    transport = paramiko.Transport((host, port))
    sock = paramiko.sock.SSHClient()
    sock.connect(host, port)
    sock.set_missing_host_key_callback(paramiko.AutoAddPolicy())
    
    sftp = sock.open_sftp()
    with open("artifact.tar.gz") as src:
        dst = f"/tmp/kharcha-tracker/kharcha_v1/kharcha_gateway/"
        sftp.put(src, dst)
```

### Dependency Parsing
```python
import re

def parse_requirements():
    with open("requirements.txt") as f:
        packages = []
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                match = re.match(r"^([a-zA-Z0-9_-]+)", line)
                if match:
                    packages.append(match.group(1))
        return packages
```

### Offline Install from Cache
```python
def install_from_cache():
    requirements = parse_requirements()
    cache_dir = Path(__file__).parent / "cache" / "pip-packages"
    
    for package in requirements:
        cache_path = cache_dir / package
        if not cache_path.exists():
            print(f"Package {package} not in cache, trying PyPI...")
            # Try PyPI
            ...
        # Install from cache
        subprocess.run(["pip", "install", "-f", str(cache_dir), str(cache_path)])
```

---

## Security Considerations

### SSH Keys
- User must configure SSH key on remote server
- Accept SSH keys from `SSH_KEY` environment variable
- Keys stored in memory, never written to disk

### Secrets
- Never include `.env` files in deployment
- Use environment variables for secrets
- Create `.env` on server manually or use secrets manager

### Permissions
- Validate write permissions on remote path
- Set appropriate file permissions on deployed artifacts

---

## Error Handling Strategy

| Error | Response |
|-------|----------|
| Venv creation fails | Clear error with Python version check |
| Offline mode: no PyPI access | Prompt user to run `pip download` to cache |
| Offline mode: cache miss | Exit with helpful message |
| SSH connection fails | Retry 3 times with backoff |
| SCP fails | Return exit code 1 with error message |
| Service won't start | Show logs, suggest troubleshooting |
| Port already in use | Suggest different port or kill process |

---

## Testing Checklist

Before deploying to production:
- [ ] Local deploy succeeds
- [ ] Dependencies install without warnings
- [ ] Tarball contains expected files
- [ ] Tarball excludes `__pycache__`, `.DS_Store`, etc.
- [ ] Remote SSH connection works
- [ ] Remote venv creation succeeds
- [ ] Service starts and responds to health check
- [ ] Graceful shutdown works

---

## Deployment Workflow

### Local Development
```bash
python deploy.py
```
- Creates `.venv` in project root
- Installs dependencies
- Starts dev server with `--reload`

### Production (Local)
```bash
python deploy.py --production
```
- Creates `.venv`
- Installs production dependencies
- Starts without `--reload`
- Binds to `0.0.0.0:8000`

### Offline Mode
```bash
python deploy.py --offline
```
- Attempts PyPI first
- Falls back to `cache/`
- Fails if packages not available

### Remote Deployment
```bash
python deploy.py --remote user@server.example.com --remote-path /var/www/kharcha/
```
- Copies tarball to remote
- Deploys to remote server
- Starts service on remote
- Returns on success

### Full Cycle
```bash
# 1. Build locally
tar -czf kharcha_gateway.tar.gz kharcha_v1/kharcha_gateway

# 2. Deploy to remote
scp kharcha_gateway.tar.gz user@server:/var/www/
ssh user@server "cd /var/www/kharcha && python deploy.py"
```

---

## Environment Variables

| Variable | Required | Description | Default |
|----------|----------|-------------|---------|
| `KHARCHA_STORAGE` | Yes | Storage backend | `flat_file` |
| `PYTHON_VERSION` | No | Python version for venv | `python3` |
| `REMOTE_USER` | Only with `--remote` | SSH username | - |
| `REMOTE_HOST` | Only with `--remote` | SSH hostname | - |
| `REMOTE_PORT` | Only with `--remote` | SSH port | `22` |
| `REMOTE_PATH` | Only with `--remote` | Deployment path | `/var/www/kharcha/` |
| `SSH_KEY_PATH` | Only with `--remote` | SSH key file | `-` (stdin) |

---

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

---

## Dependencies (for deploy.py)

```bash
pip install -U pip setuptools wheel
pip install pyyaml
pip install paramiko
pip install requests  # for PyPI API
```

Note: `pyyaml` for reading `requirements.txt` if needed, `paramiko` for SSH, `requests` for PyPI API calls.

---

## Timeline

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| 1 | 1 day | `deploy.py` skeleton with arg parsing |
| 2 | 2 days | Venv creation and dependency installation |
| 3 | 1 day | Offline mode implementation |
| 4 | 1 day | Tarball creation |
| 5 | 1 day | Remote SSH deployment |
| 6 | 1 day | Testing and error handling |
| 7 | 1 day | Documentation and final polish |

---

## Approval Required

Please review and approve:
1. ✅ Script type: **deploy.py** (Python)
2. ✅ Remote deploy scope: **Full deploy** (venv + install + start)
3. ✅ Offline cache management: **Auto-download PyPI to cache**
4. ✅ Remote venv: **System-native venv** on remote server
5. ✅ Include frontend in tarball: **No** — separate deployment

Once approved, I will proceed with implementation.

---

## Implementation Status

**Pending user approval on key decisions above.**

Upon approval, the following will be created:
1. `deploy.py` — Complete deployment script
2. `cache/` — Directory structure for offline packages
3. `Makefile` — Convenience commands
4. `DEPLOYMENT.md` — User documentation