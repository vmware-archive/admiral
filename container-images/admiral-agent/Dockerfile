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

RUN tyum install procps-ng -y && tyum clean all

# Used by ShellInABox
EXPOSE 4200

ENTRYPOINT ["/bin/bash", "/entrypoint.sh"]

COPY /tocopy /

