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

FROM vmware/photon:1.0

MAINTAINER Admiral Team, https://vmware.github.io/admiral/

WORKDIR /app

COPY app/closure_module/package.json /app/closure_module/package.json

RUN tyum install wget curl npm tar gzip -y &&\
    wget http://nodejs.org/dist/v4.7.2/node-v4.7.2-linux-x64.tar.gz &&\
    tar --strip-components 1 -xzvf node-v4.7.2-linux-x64.tar.gz -C /usr/local &&\
    mkdir -p /app/closure_module &&\
    npm install /app/closure_module --production &&\
    rm -fr ~/.npm &&\
    rm -fr node-v4.7.2-linux-x64.tar.gz &&\
    tyum remove wget tar gzip vim findutils -y  &&\
    tyum clean all

