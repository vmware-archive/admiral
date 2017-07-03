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
FROM vmware/photon:1.0

MAINTAINER Admiral Team, https://vmware.github.io/admiral/

RUN tdnf -y install \
    haproxy \
    inotify-tools \
    socat

COPY files/ /

RUN chmod 755 /etc/container-boot/entrypoint \
    && chmod 755 /etc/container-boot/.base-entrypoint.sh \
    && chmod 755 /etc/container-boot/healthcheck.sh \
    && chmod 755 /etc/container-boot/run.sh \
    && chmod 666 /etc/haproxy/* \
    && chmod 755 /usr/bin/proxy-config \
    && sync \
    && mkdir /var/lib/haproxy

HEALTHCHECK --interval=5s --timeout=20s --retries=24 \
    CMD /etc/container-boot/healthcheck.sh

ENTRYPOINT ["/etc/container-boot/.base-entrypoint.sh"]
