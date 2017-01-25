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

RUN tyum install python3-devel openssl-devel libffi dbus-glib python3 \
    gcc make binutils glibc-devel linux-api-headers -y &&\
    mkdir -p /app &&\
    pip3 install requests &&\
    tyum remove vim findutils -y  &&\
    tyum clean all

WORKDIR /app
