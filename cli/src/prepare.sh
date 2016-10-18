#!/bin/bash
# Copyright (c) 2016 VMware, Inc. All Rights Reserved.
#
# This product is licensed to you under the Apache License, Version 2.0 (the "License").
# You may not use this product except in compliance with the License.
#
# This product may include a number of subcomponents with separate copyright notices
# and license terms. Your use of these subcomponents is subject to the terms and
# conditions of the subcomponent's license, as noted in the LICENSE file.

github_deps=("github.com/mitchellh/go-homedir:756f7b183b7ab78acdbbee5c7f392838ed459dda"
             "github.com/spf13/cobra:9c28e4bbd74e5c3ed7aacbc552b2cab7cfdfe744"
             "github.com/spf13/pflag:7b17cc4658ef5ca157b986ea5c0b43af7938532b"
             "github.com/inconshreveable/mousetrap:76626ae9c91c4f2a10f34cad8ce83ea42c93bb75")


golang_deps=("golang/crypto:e311231e83195f401421a286060d65643f9c9d40")


for i in "${github_deps[@]}"
do
    git_dep=${i%%:*}
    git_commit=${i#*:}
    if [ -d "${git_dep}" ]; then
        git_currentHash="$(git -C ${git_dep} rev-parse HEAD)"

        if [ ${git_commit} == "${git_currentHash}" ]; then
            continue
        fi
        git -C ${git_dep} checkout ${git_commit}
        continue
    else
        ./github-checkout.sh ${git_dep} ${git_commit}
    fi
done


for j in "${golang_deps[@]}"
do
    golang_dep=${j%%:*}
    golang_commit=${j#*:}
    golang_repo=$(echo ${golang_dep} | cut -d "/" -f 2)
    directory="golang.org/x/${golang_repo}"

    if [ -d "${directory}" ]; then
        golang_currentHash="$(git -C ${directory} rev-parse HEAD)"

        if [ ${golang_commit} == "${golang_currentHash}" ]; then
            continue
        fi

        git -C ${directory} checkout ${golang_commit}
        continue
    else
        ./golang-checkout.sh ${golang_dep} ${golang_commit}
    fi
done
