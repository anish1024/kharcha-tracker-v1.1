# Run from the repository root:
#   make -f deployment/Makefile <target>
# or from this directory:
#   cd deployment && make <target>

DEPLOY_DIR := $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
REPO_ROOT  := $(abspath $(DEPLOY_DIR)/..)
KT_DIR     := $(REPO_ROOT)/kharcha-tracker

.PHONY: local production offline tarball remote remote-start build cache clean help

help:
	@echo "Kharcha Tracker deployment targets:"
	@echo "  make local          - Local dev deploy + start (with --reload)"
	@echo "  make production     - Local production deploy + start"
	@echo "  make offline        - Deploy using local cache (offline mode)"
	@echo "  make tarball        - Build kharcha_gateway.tar.gz only"
	@echo "  make remote         - Remote deploy (set REMOTE=user@host)"
	@echo "  make remote-start   - Remote deploy + start service"
	@echo "  make cache          - Populate offline package cache"
	@echo "  make clean          - Remove venv, tarball, cache"
	@echo ""
	@echo "Examples:"
	@echo "  make -f deployment/Makefile remote REMOTE=deploy@192.168.1.10"
	@echo "  make -f deployment/Makefile remote-start REMOTE=deploy@192.168.1.10:2222 REMOTE_PATH=/opt/kharcha"

local:
	python3 $(DEPLOY_DIR)/deploy.py

production:
	python3 $(DEPLOY_DIR)/deploy.py --production

offline:
	python3 $(DEPLOY_DIR)/deploy.py --offline

tarball:
	python3 $(DEPLOY_DIR)/deploy.py --tarball

# Usage: make remote REMOTE=user@host [REMOTE_PATH=/opt/kharcha]
remote:
ifndef REMOTE
	$(error REMOTE is not set. Usage: make remote REMOTE=user@host)
endif
	python3 $(DEPLOY_DIR)/deploy.py --remote $(REMOTE) $(if $(REMOTE_PATH),--remote-path $(REMOTE_PATH),)

# Usage: make remote-start REMOTE=user@host [REMOTE_PATH=/opt/kharcha]
remote-start:
ifndef REMOTE
	$(error REMOTE is not set. Usage: make remote-start REMOTE=user@host)
endif
	python3 $(DEPLOY_DIR)/deploy.py --remote $(REMOTE) --remote-start $(if $(REMOTE_PATH),--remote-path $(REMOTE_PATH),)

# Populate the offline cache from PyPI (requires network)
cache:
	mkdir -p $(KT_DIR)/cache/pip-packages
	python3 -m pip download -r $(KT_DIR)/requirement.txt -d $(KT_DIR)/cache/pip-packages

build: tarball

clean:
	rm -rf $(KT_DIR)/.venv $(KT_DIR)/kharcha_gateway.tar.gz $(KT_DIR)/cache/
