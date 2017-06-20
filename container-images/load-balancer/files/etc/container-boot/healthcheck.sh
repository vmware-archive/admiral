#
# Copyright (c) 2017 VMware, Inc. All Rights Reserved.
#
# This product is licensed to you under the Apache License, Version 2.0 (the "License").
# You may not use this product except in compliance with the License.
#
# This product may include a number of subcomponents with separate copyright notices
# and license terms. Your use of these subcomponents is subject to the terms and
# conditions of the subcomponent's license, as noted in the LICENSE file.
#

#!/bin/sh

HA_PROXY_UNIX_SOCK=/run/haproxy.sock

if echo "show info" | socat ${HA_PROXY_UNIX_SOCK} stdio
then
    echo "Reverse-proxy is listening on unix socket: ${HA_PROXY_UNIX_SOCK}"
    exit 0
else
    echo "Reverse-proxy failed to respond on unix socket: ${HA_PROXY_UNIX_SOCK}"
    exit 1
fi

