#
# Copyright (c) 2016 VMware, Inc. All Rights Reserved.
#
# This product is licensed to you under the Apache License, Version 2.0 (the "License").
# You may not use this product except in compliance with the License.
#
# This product may include a number of subcomponents with separate copyright notices
# and license terms. Your use of these subcomponents is subject to the terms and
# conditions of the subcomponent's license, as noted in the LICENSE file.
#

#!/bin/sh

touch /var/log/shellserver.log

if [ ! -f "/agent/server.key" ] && [ ! -f "/agent/server.crt" ]; then
    #Generate Admiral Agent server certificate
    openssl req -new -newkey rsa:2048 -days 3650 -nodes -sha256 -x509 -subj "/CN=admiral-agent" -keyout /agent/server.key -out /agent/server.crt
fi

cat admiral_logo.txt
echo "Admiral Agent started successfully!"

/agent/shellserver