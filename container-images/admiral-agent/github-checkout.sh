#!/bin/sh

DEPENDENCY=$1

DEP_NAMESPACE=$(echo $DEPENDENCY | cut -d "/" -f 1)
DEP_REPOSITORY=$(echo $DEPENDENCY | cut -d "/" -f 2)

CHANGESET=$2

mkdir -p /go/src/github.com/${DEP_NAMESPACE} && \
    cd /go/src/github.com/${DEP_NAMESPACE} && \
    git clone https://github.com/${DEPENDENCY}.git && \
    cd ${DEP_REPOSITORY} && \
    git checkout $CHANGESET