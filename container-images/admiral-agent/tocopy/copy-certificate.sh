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

# Instruct docker daemon to trust registry certificate. See
# https://docs.docker.com/docker-trusted-registry/userguide/
# https://docs.docker.com/registry/insecure/
set -e

HOSTNAME=$1
CERT=$2

CERTS_DIR=/etc/docker/certs.d
CERT_DIR=$CERTS_DIR/$HOSTNAME
CERT_FILE=$CERT_DIR/ca.crt

mkdir -p $CERT_DIR
echo "$CERT" > $CERT_FILE

# Return a message to prevent errors while retrieving
# the response body in ShellContainerExecutorService
echo "success!";
