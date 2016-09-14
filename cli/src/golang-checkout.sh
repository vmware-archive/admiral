#!/bin/sh

DEPENDENCY=$1

DEP_NAMESPACE=$(echo $DEPENDENCY | cut -d "/" -f 1)
DEP_REPOSITORY=$(echo $DEPENDENCY | cut -d "/" -f 2)

CHANGESET=$2

mkdir -p ./golang.org/x/${DEP_NAMESPACE} && \
    cd ./golang.org/x/${DEP_NAMESPACE} && \
    git clone https://github.com/${DEPENDENCY}.git && \
    cd ${DEP_REPOSITORY} && \
    git checkout $CHANGESET && \
    cd ../.. && \
    mv ${DEPENDENCY} ./ && \
    rmdir ${DEP_NAMESPACE}