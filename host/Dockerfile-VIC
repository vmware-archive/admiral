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

FROM vmware/admiral-base

ENTRYPOINT ["/entrypoint.sh"]

COPY entrypoint.sh /entrypoint.sh
COPY configuration-vic.properties $DIST_CONFIG_FILE_PATH
COPY logging-vic.properties $LOG_CONFIG_FILE_PATH

RUN mkdir $ADMIRAL_ROOT/log

COPY images-bin/admiral_agent.* $USER_RESOURCES
COPY target/lib target/admiral-host-*.jar $ADMIRAL_ROOT/
