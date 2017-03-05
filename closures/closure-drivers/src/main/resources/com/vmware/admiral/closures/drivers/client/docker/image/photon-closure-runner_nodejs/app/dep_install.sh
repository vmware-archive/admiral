#!/bin/bash

echo "${TRUST_CERTS}" > trust.pem

node ./depmain.js

