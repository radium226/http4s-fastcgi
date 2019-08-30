#!/bin/bash
echo "Content-Type: text/plain"
echo
env | grep -E '^(FCGI_SCRIPT_FILENAME|REQUEST_METHOD|QUERY_STRING)='
