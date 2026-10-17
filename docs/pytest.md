## Using `pytest`

To run `pytest`, we need to set repository root in Python's import search path (`sys.path`), else it cannot resolve `kharcha_v1` as a top-level package.

Here are the ways to fix this, ordered from quickest to best practice:

---

### Solution 1: Run pytest as a module (Quickest fix)

Run `pytest` via `python -m`:

```bash
python -m pytest

```

When you invoke `python -m pytest`, Python automatically adds your current working directory to `sys.path`, allowing `kharcha_v1` to be imported cleanly.

---

### Solution 2: Set `PYTHONPATH`

Temporarily prepend your project root to `PYTHONPATH`:

* **Linux / macOS:**
```bash
PYTHONPATH=. pytest

```


* **Windows (PowerShell):**
```powershell
$env:PYTHONPATH="."
pytest

```


* **Windows (CMD):**
```cmd
set PYTHONPATH=.
pytest

```

---

### Solution 3: Add `pytest.ini` or `pyproject.toml` (Permanent fix)

Place a configuration file in your project root (the directory containing `kharcha_v1`). `pytest` will automatically configure `sys.path`.

#### Option A: `pytest.ini`

Create `pytest.ini` in the repository root:

```ini
[pytest]
pythonpath = .

```

#### Option B: `pyproject.toml`

If your project uses `pyproject.toml`, add:

```toml
[tool.pytest.ini_options]
pythonpath = ["."]

```

##### Why this is recommended:

- Works consistently across all developer machines, CI/CD pipelines, and IDEs (VS Code, PyCharm).

- Eliminates the need to remember shell-specific flags or environment variable exports (export PYTHONPATH=...).

- You and any other contributor can simply type pytest from the root directory.

---

### Verification

Ensure you are executing the command from the root directory that directly contains the `kharcha_v1` folder:

```text
<project-root>/
├── kharcha_v1/
│   ├── kharcha_gateway/
│   └── tests/
│       └── conftest.py
└── pytest.ini       <-- (if using Solution 3)

```

Then run:

```bash
python -m pytest

```

---

### Common use cases

To run a single test file with `pytest`, specify the relative or absolute path to the file as an argument:

```bash
pytest path/to/test_file.py

```

For your project structure, run it from the root directory:

```bash
python -m pytest kharcha_v1/tests/test_auth_service.py

```

*(Using `python -m pytest` ensures your current working directory is added to `sys.path`).*

---

#### Useful Variations

* **Run with verbose output:**
```bash
python -m pytest kharcha_v1/tests/test_auth_service.py -v

```


* **Run a single test function inside that file:**
Use the `::` operator followed by the function name:
```bash
python -m pytest kharcha_v1/tests/test_auth_service.py::test_signup_success

```


* **Run a single test method inside a test class:**
```bash
python -m pytest path/to/test_file.py::TestClassName::test_method_name

```


* **Run tests matching an expression/name in that file:**
Use the `-k` flag:
```bash
python -m pytest kharcha_v1/tests/test_auth_service.py -k "signup"

```