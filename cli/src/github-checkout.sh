#!/bin/sh

DEPENDENCY=$1

DEP_NAMESPACE=$(echo $DEPENDENCY | cut -d "/" -f 2)
DEP_REPOSITORY=$(echo $DEPENDENCY | cut -d "/" -f 3)

CHANGESET=$2

mkdir -p ./github.com/${DEP_NAMESPACE} && \
    cd ./github.com/${DEP_NAMESPACE} && \
    git clone https://${DEPENDENCY}.git && \
    cd ${DEP_REPOSITORY} && \
    git checkout $CHANGESET
