#!/bin/bash
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

function log() {
    echo "`date '+%Y-%m-%d %H:%M:%S'` $@"
}

log "Booting..."

readonly FB_SCRIPTS_DIR=$( cd -L $( dirname $(readlink -f "${BASH_SOURCE[0]}") ) && pwd )
readonly ON_RUN="${FB_SCRIPTS_DIR}/run.sh"
readonly ON_START="${FB_SCRIPTS_DIR}/start.sh"
readonly ENTRYPOINT="${FB_SCRIPTS_DIR}/entrypoint"

log "Calling ${ON_RUN}..."
. "${ON_RUN}"
log "Calling ${ON_RUN} DONE."

log "Running ${ENTRYPOINT}..."
"${ENTRYPOINT}"
