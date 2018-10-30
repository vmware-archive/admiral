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

RUN tdnf distro-sync --refresh -y && \
    tdnf install gzip python3-devel openssl-devel libffi dbus-glib python3-pip \
    gcc make binutils glibc-devel linux-api-headers -y && \
    mkdir -p /app && \
    pip3 install --upgrade pip setuptools wheel && \
    pip3 install requests && \
    tdnf remove vim findutils -y  && \
    tdnf clean all && \
    rm -fr /var/cache/tdnf/*

WORKDIR /app
