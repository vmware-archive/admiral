#
# Copyright (c) 2016-2019 VMware, Inc. All Rights Reserved.
#
# This product is licensed to you under the Apache License, Version 2.0 (the "License").
# You may not use this product except in compliance with the License.
#
# This product may include a number of subcomponents with separate copyright notices
# and license terms. Your use of these subcomponents is subject to the terms and
# conditions of the subcomponent's license, as noted in the LICENSE file.
#

FROM photon:2.0-20190511

MAINTAINER Admiral Team, https://vmware.github.io/admiral/

RUN tdnf distro-sync --refresh -y && \
    tdnf install -y shadow && \
    tdnf install -y openjre8 && \
    tdnf clean all && \
    mkdir -p /usr/lib/jvm && \
    export JAVA_HOME="/usr/lib/jvm/default-jvm" && \
    ln -s /var/opt/$(ls /var/opt/ | grep OpenJDK) $JAVA_HOME && \
    ln -s $JAVA_HOME/bin/* /usr/bin/

ENV ADMIRAL_PORT=8282 \
    ADMIRAL_STORAGE_PATH=/var/admiral/ \
    USER_RESOURCES=/etc/xenon/user-resources/system-images/ \
    ADMIRAL_ROOT=/admiral \
    MOCK_MODE=false

ENV DIST_CONFIG_FILE_PATH $ADMIRAL_ROOT/config/dist_configuration.properties
ENV CONFIG_FILE_PATH $ADMIRAL_ROOT/config/configuration.properties
ENV LOG_CONFIG_FILE_PATH $ADMIRAL_ROOT/config/logging.properties

RUN mkdir $ADMIRAL_ROOT && \
    mkdir -p $ADMIRAL_STORAGE_PATH && \
    mkdir -p $USER_RESOURCES

EXPOSE $ADMIRAL_PORT

# Create an admiral user so the application doesn't run as root.
RUN groupadd -g 10000 admiral && \
    useradd -u 10000 -g admiral -d /admiral -s /sbin/nologin -c "Admiral user" admiral

# Set the home directory to the admiral user home.
ENV HOME $ADMIRAL_ROOT
WORKDIR $ADMIRAL_ROOT

# Chown all the files to the admiral user.
RUN chown -v -R admiral:admiral /admiral && \
    chown -v -R admiral:admiral /var/admiral && \
    chown -v -R admiral:admiral /etc/xenon

# Change to the admiral user.
USER admiral