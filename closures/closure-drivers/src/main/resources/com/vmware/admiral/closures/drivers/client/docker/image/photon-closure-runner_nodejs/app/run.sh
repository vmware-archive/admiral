#!/bin/bash

[[ ! -z "${TRUST_CERTS}" ]] && echo "${TRUST_CERTS}" | base64 --decode > trusted.gz
[[ ! -z "${TRUST_CERTS}" ]] && gzip -dc < trusted.gz > trust.pem

node ./appmain.js

#PATCH the closure with the response saved by node in file response.json
curl -sL --request PATCH -H "Content-Type: application/json" \
                -H "x-xenon-auth-token: ${TOKEN}" -d @response.json ${TASK_URI} > /dev/null || \
curl -sL --cacert trust.pem \
         --request PATCH -H "Content-Type: application/json" \
                         -H "x-xenon-auth-token: ${TOKEN}" -d @response.json ${TASK_URI} > /dev/null

