#!/bin/bash

mkdir ./user_scripts || echo "File exits"
echo "${TRUST_CERTS}" > trust.pem
cd user_scripts
python3 -u ../appmain.py

