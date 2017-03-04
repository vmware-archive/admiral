#!/bin/bash

echo "${TRUST_CERTS}" >> trust.pem

node ./appmain.js

#PATCH the closure with the response saved by node in file response.json
curl -sL --cacert trust.pem \
         --request PATCH -H "Content-Type: application/json" \
                         -H "x-xenon-auth-token: ${TOKEN}" -d @response.json ${TASK_URI} > /dev/null
