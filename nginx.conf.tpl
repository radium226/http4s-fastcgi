daemon off;
worker_processes 2;

events {
  use epoll;
  worker_connections 128;
}

error_log /dev/stdout error;

http {
  server_tokens off;
  include mime.types;
  charset utf-8;
  access_log /dev/stdout;
  server {
    server_name localhost;
    listen 127.0.0.1:%{PORT};
    error_page 500 502 503 504 /50x.html;
    location / {
      include fastcgi_params;
      fastcgi_param	SCRIPT_FILENAME %{FCGI_SCRIPT_FILENAME};
      fastcgi_param	GIT_HTTP_EXPORT_ALL	"";
      fastcgi_param	GIT_PROJECT_ROOT %{WORK_FOLDER_PATH}/git;
      fastcgi_param	PATH_INFO	$uri;
      fastcgi_pass unix:%{WORK_FOLDER_PATH}/fcgiwrap/%{FCGIWRAP_SOCK_NAME};
    }
  }
}
