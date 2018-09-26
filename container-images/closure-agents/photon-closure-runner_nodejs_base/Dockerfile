#
# Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
#
# This product is licensed to you under the Apache License, Version 2.0 (the "License").
# You may not use this product except in compliance with the License.
#
# This product may include a number of subcomponents with separate copyright notices
# and license terms. Your use of these subcomponents is subject to the terms and
# conditions of the subcomponent's license, as noted in the LICENSE file.
#

FROM photon:2.0

MAINTAINER Admiral Team, https://vmware.github.io/admiral/

WORKDIR /app

COPY app/closure_module/package.json /app/closure_module/package.json

RUN tdnf distro-sync --refresh -y && \
    tdnf install wget curl npm tar gzip libstdc++-6.3.0 -y && \
    wget http://nodejs.org/dist/v4.8.1/node-v4.8.1-linux-x64.tar.gz && \
    tar --strip-components 1 -xzvf node-v4.8.1-linux-x64.tar.gz -C /usr/local && \
    mkdir -p /app/closure_module && \
    npm install /app/closure_module --production && \
    rm -fr ~/.npm && \
    rm -fr node-v4.8.1-linux-x64.tar.gz && \
    tdnf remove wget tar vim findutils -y  && \
    tdnf clean all && \
    rm -fr /var/cache/tdnf/*
