#!/bin/bash

mkdir ./user_scripts || echo "File exits"
[[ ! -z "${TRUST_CERTS}" ]] && echo "${TRUST_CERTS}" > trust.pem

java -cp .:\* com/vmware/admiral/closure/runner/AppRunner