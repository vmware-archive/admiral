#!/bin/bash

mkdir ./user_scripts || echo "File exits"

[[ ! -z "${TRUST_CERTS}" ]] && echo "${TRUST_CERTS}" | base64 --decode > trusted.gz
[[ ! -z "${TRUST_CERTS}" ]] && gzip -dc < trusted.gz > trust.pem

cd user_scripts
python3 -u ../appmain.py