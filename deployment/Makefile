# Run from the repository root:
#   make -f deployment/Makefile <target>
# or from this directory:
#   cd deployment && make <target>

DEPLOY_DIR := $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
REPO_ROOT  := $(abspath $(DEPLOY_DIR)/..)
KT_DIR     := $(REPO_ROOT)/kharcha-tracker

# Optional service port: make local PORT=9000
PORT_ARGS  := $(if $(PORT),--port $(PORT),)

.PHONY: local production offline tarball remote remote-start backup restore remote-backup remote-restore build cache clean help

help:
	@echo "Kharcha Tracker deployment targets:"
	@echo "  make local          - Local dev deploy + start (with --reload)"
	@echo "                       (any target: append PORT=<n>, default 8000)"
	@echo "  make production     - Local production deploy + start"
	@echo "  make offline        - Deploy using local cache (offline mode)"
	@echo "  make tarball        - Build kharcha_gateway.tar.gz only"
	@echo "  make remote         - Remote deploy (set REMOTE=user@host)"
	@echo "  make remote-start   - Remote deploy + start service"
	@echo "  make backup         - Back up the local installation"
	@echo "  make restore        - Restore the local installation from latest backup"
	@echo "  make remote-backup  - Back up the remote installation (REMOTE=user@host)"
	@echo "  make remote-restore - Restore the remote installation (REMOTE=user@host)"
	@echo "  make cache          - Populate offline package cache"
	@echo "  make clean          - Remove venv, tarball, cache (keeps backups)"
	@echo ""
	@echo "Examples:"
	@echo "  make -f deployment/Makefile remote REMOTE=deploy@192.168.1.10"
	@echo "  make -f deployment/Makefile remote-start REMOTE=deploy@192.168.1.10:2222 REMOTE_PATH=/opt/kharcha"

local:
	python3 $(DEPLOY_DIR)/deploy.py $(PORT_ARGS)

production:
	python3 $(DEPLOY_DIR)/deploy.py --production $(PORT_ARGS)

offline:
	python3 $(DEPLOY_DIR)/deploy.py --offline $(PORT_ARGS)

tarball:
	python3 $(DEPLOY_DIR)/deploy.py --tarball

# Usage: make remote REMOTE=user@host [REMOTE_PATH=/opt/kharcha]
remote:
ifndef REMOTE
	$(error REMOTE is not set. Usage: make remote REMOTE=user@host)
endif
	python3 $(DEPLOY_DIR)/deploy.py --remote $(REMOTE) $(PORT_ARGS) $(if $(REMOTE_PATH),--remote-path $(REMOTE_PATH),)

# Usage: make remote-start REMOTE=user@host [REMOTE_PATH=/opt/kharcha]
remote-start:
ifndef REMOTE
	$(error REMOTE is not set. Usage: make remote-start REMOTE=user@host)
endif
	python3 $(DEPLOY_DIR)/deploy.py --remote $(REMOTE) --remote-start $(PORT_ARGS) $(if $(REMOTE_PATH),--remote-path $(REMOTE_PATH),)

# Populate the offline cache from PyPI (requires network)
cache:
	mkdir -p $(KT_DIR)/cache/pip-packages
	python3 -m pip download -r $(KT_DIR)/requirement.txt -d $(KT_DIR)/cache/pip-packages

backup:
	python3 $(DEPLOY_DIR)/deploy.py --backup

restore:
	python3 $(DEPLOY_DIR)/deploy.py --restore $(PORT_ARGS)

# Usage: make remote-backup REMOTE=user@host [REMOTE_PATH=/opt/kharcha]
remote-backup:
ifndef REMOTE
	$(error REMOTE is not set. Usage: make remote-backup REMOTE=user@host)
endif
	python3 $(DEPLOY_DIR)/deploy.py --remote $(REMOTE) --backup $(if $(REMOTE_PATH),--remote-path $(REMOTE_PATH),)

# Usage: make remote-restore REMOTE=user@host [REMOTE_PATH=/opt/kharcha]
remote-restore:
ifndef REMOTE
	$(error REMOTE is not set. Usage: make remote-restore REMOTE=user@host)
endif
	python3 $(DEPLOY_DIR)/deploy.py --remote $(REMOTE) --restore $(PORT_ARGS) $(if $(REMOTE_PATH),--remote-path $(REMOTE_PATH),)

build: tarball

clean:
	rm -rf $(KT_DIR)/.venv $(DEPLOY_DIR)/kharcha_gateway.tar.gz $(KT_DIR)/cache/
