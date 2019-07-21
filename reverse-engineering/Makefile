# https://www.netnea.com/cms/nginx-tutorial-2_minimal-nginx-configuration/
SHELL=/bin/bash
.SHELLFLAGS = -e -u -c
.ONESHELL:

WORK_FOLDER_PATH = work
PORT = 8080

$(WORK_FOLDER_PATH)/nginx/nginx.conf: $(WORK_FOLDER_PATH)/nginx/mime.types $(WORK_FOLDER_PATH)/nginx/fastcgi_params
	mkdir -p "$(WORK_FOLDER_PATH)/nginx"
	sed -r \
		-e "s,%\{WORK_FOLDER_PATH\},$$( readlink -f '$(WORK_FOLDER_PATH)' ),g" \
		-e "s,%\{PORT\},$(PORT),g" \
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
		"/etc/nginx/fastcgi_params" \
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
		-s "unix:$(WORK_FOLDER_PATH)/fcgiwrap/fcgiwrap.sock"

.PHONY: nginx
nginx: $(WORK_FOLDER_PATH)/nginx/nginx.conf $(WORK_FOLDER_PATH)/git/config
	nginx \
		-g "pid nginx.pid;" \
		-p "$(WORK_FOLDER_PATH)/nginx" \
		-c "nginx.conf"

.PHONY: supervisor
supervisor:
	mkdir -p "$(WORK_FOLDER_PATH)/supervisor"
	WORK_FOLDER_PATH="$(WORK_FOLDER_PATH)" \
		supervisord \
			-n \
			-d "$(WORK_FOLDER_PATH)/supervisor" \
			-c "supervisor.conf"

.PHONY: git-clone
git-clone:
	mkdir -p "$(WORK_FOLDER_PATH)/git-clone"
	cd "$(WORK_FOLDER_PATH)/git-clone"
	git clone "http://localhost:$(PORT)" "."

.PHONY: clean
clean:
	rm -Rf "$(WORK_FOLDER_PATH)" || true
