# Reverse Engineering

## Prerequisites
You need to install `supervisor`, 'fcgiwrap', 'nginx' and 'git'.

## Targets
* `make supervisor`: it runs `make nginx` and `make fcgiwrap` concurrently
* `make fcgiwrap`: as far as I get it, it's a bridge which allows to use CGI application using the FastCGI protocol and you can use this target to run it
* `make nginx`: it expose a git repository using `git-http-backend` (through `fcgiwrap`), but you can specify the application to use using the `FCGI_SCRIPT_FILENAME` variable
* `make git-clone`: it clone the exposed repository
* `make curl`: it will make a HTTP call to the started `nginx` server
