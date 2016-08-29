#!/bin/bash
#
# Tested on vRA VA (Linux 3.0.101-0.47.71-default x86_64) with:
# echo (GNU coreutils) 8.12
# cat (GNU coreutils) 8.12
# od (GNU coreutils) 8.12
# GNU sed version 4.1.5
# OpenSSL 1.0.2g 1 Mar 2016
#

input=$1

key_string="$(cat encryption.key | od -t x1 | sed -r 's/^.{7}//' | sed ':a;N;$!ba;s/\n//g;s/ //g')"
iv_data=${key_string:0:32}
key_data=${key_string:32}

output=$(echo -n $input | openssl enc -aes-256-cbc -e -base64 -A -K $key_data -iv $iv_data)

echo -n $output
