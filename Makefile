# https://www.netnea.com/cms/nginx-tutorial-2_minimal-nginx-configuration/
SHELL=/bin/bash
.SHELLFLAGS = -e -u -c
.ONESHELL:

WORK_FOLDER_PATH = /tmp/reverse-engineering/work
PORT = 8080
FCGIWRAP_SOCK_NAME = fcgiwrap.sock
FCGI_SCRIPT_FILENAME = /usr/lib/git-core/git-http-backend

$(WORK_FOLDER_PATH)/nginx/nginx.conf: $(WORK_FOLDER_PATH)/nginx/mime.types $(WORK_FOLDER_PATH)/nginx/fastcgi_params
	mkdir -p "$(WORK_FOLDER_PATH)/nginx"
	sed -r \
		-e "s,%\{WORK_FOLDER_PATH\},$$( readlink -f '$(WORK_FOLDER_PATH)' ),g" \
		-e "s,%\{PORT\},$(PORT),g" \
		-e "s,%\{FCGIWRAP_SOCK_NAME\},$(FCGIWRAP_SOCK_NAME),g" \
		-e "s,%\{FCGI_SCRIPT_FILENAME\},$(FCGI_SCRIPT_FILENAME),g" \
		"nginx.conf.tpl" \
		> "$(WORK_FOLDER_PATH)/nginx/nginx.conf"
	nginx \
		-t \
		-g "pid nginx.pid;" \
		-p "$(WORK_FOLDER_PATH)/nginx" \
		-c "nginx.conf"

$(WORK_FOLDER_PATH)/nginx/mime.types:
	mkdir -p "$(WORK_FOLDER_PATH)/nginx"
	cp \
		"/etc/nginx/mime.types" \
		"$(WORK_FOLDER_PATH)/nginx/mime.types"

$(WORK_FOLDER_PATH)/nginx/fastcgi_params:
	mkdir -p "$(WORK_FOLDER_PATH)/nginx"
	cp \
		"fastcgi_params" \
		"$(WORK_FOLDER_PATH)/nginx/fastcgi_params"

$(WORK_FOLDER_PATH)/git/config:
	mkdir -p "$(WORK_FOLDER_PATH)/git"
	git init --bare "$(WORK_FOLDER_PATH)/git" --shared
	git config --file "$(WORK_FOLDER_PATH)/git/config" "http.receivepack" "true"


.PHONY: fcgiwrap
fcgiwrap:
	mkdir -p "$(WORK_FOLDER_PATH)/fcgiwrap"
	fcgiwrap \
		-c 2 \
		-s "unix:$(WORK_FOLDER_PATH)/fcgiwrap/$(FCGIWRAP_SOCK_NAME)"

.PHONY: nginx
nginx: $(WORK_FOLDER_PATH)/nginx/nginx.conf $(WORK_FOLDER_PATH)/git/config
	nginx \
		-g "pid nginx.pid;" \
		-p "$(WORK_FOLDER_PATH)/nginx" \
		-c "nginx.conf"

.PHONY: supervisor
supervisor:
	mkdir -p "$(WORK_FOLDER_PATH)/supervisor"
	NODE_PATH="/usr/lib/node_modules" \
	WORK_FOLDER_PATH="$(WORK_FOLDER_PATH)" \
	FCGI_SCRIPT_FILENAME="$(FCGI_SCRIPT_FILENAME)" \
		supervisord \
			-n \
			-d "$(WORK_FOLDER_PATH)/supervisor" \
			-c "supervisor.conf"

.PHONY: git-clone
git-clone:
	mkdir -p "$(WORK_FOLDER_PATH)/git-clone"
	cd "$(WORK_FOLDER_PATH)/git-clone"
	git clone "http://localhost:$(PORT)" "."

.PHONY: curl
	curl "http://localhost:$(PORT)"

.PHONY: clean
clean:
	rm -Rf "$(WORK_FOLDER_PATH)" || true
