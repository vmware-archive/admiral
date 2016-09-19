#!/bin/sh
# Copyright (c) 2016 VMware, Inc. All Rights Reserved.
#
# This product is licensed to you under the Apache License, Version 2.0 (the "License").
# You may not use this product except in compliance with the License.
#
# This product may include a number of subcomponents with separate copyright notices
# and license terms. Your use of these subcomponents is subject to the terms and
# conditions of the subcomponent's license, as noted in the LICENSE file.

DEPENDENCY=$1

DEP_NAMESPACE=$(echo $DEPENDENCY | cut -d "/" -f 1)
DEP_REPOSITORY=$(echo $DEPENDENCY | cut -d "/" -f 2)

CHANGESET=$2

mkdir -p ./golang.org/x/${DEP_NAMESPACE} && \
    cd ./golang.org/x/${DEP_NAMESPACE} && \
    git clone https://github.com/${DEPENDENCY}.git && \
    cd ${DEP_REPOSITORY} && \
    git checkout $CHANGESET && \
    cd ../.. && \
    mv ${DEPENDENCY} ./ && \
    rmdir ${DEP_NAMESPACE}