#!/bin/bash
{
	echo "Content-Type: text/plain"
	echo "" | tee -a "./hello.log"
	echo "Hello, World! "
} | tee "./hello.log"
exit 0
