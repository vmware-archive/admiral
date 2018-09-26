#
# Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
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

RUN tdnf distro-sync --refresh -y && \
    tdnf install wget curl tar gzip -y && \
    tdnf install openjdk8 -y && \
    wget https://repo1.maven.org/maven2/com/google/code/gson/gson/2.6.2/gson-2.6.2.jar && \
    wget http://central.maven.org/maven2/org/apache/httpcomponents/httpclient/4.5/httpclient-4.5.jar && \
    wget http://central.maven.org/maven2/org/apache/httpcomponents/httpcore/4.4.1/httpcore-4.4.1.jar && \
    wget http://central.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar && \
    rm -fr jdk-8u121-linux-x64.tar.gz && \
    tdnf remove wget tar vim -y  && \
    tdnf clean all && \
    rm -fr /var/cache/tdnf/*
