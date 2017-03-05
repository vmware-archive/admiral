#!/usr/bin/env bash

echo "${TRUST_CERTS}" > trust.pem

python3 -u /app/dep_install.py
