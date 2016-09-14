#!/bin/bash
declare -A github_deps
github_deps["github.com/mitchellh/go-homedir"]="756f7b183b7ab78acdbbee5c7f392838ed459dda"
github_deps["github.com/spf13/cobra"]="9c28e4bbd74e5c3ed7aacbc552b2cab7cfdfe744"
github_deps["github.com/spf13/pflag"]="7b17cc4658ef5ca157b986ea5c0b43af7938532b"
github_deps["github.com/inconshreveable/mousetrap"]="76626ae9c91c4f2a10f34cad8ce83ea42c93bb75"

declare -A golang_deps
golang_deps["golang/crypto"]="e311231e83195f401421a286060d65643f9c9d40"


for git_dep in "${!github_deps[@]}"
do
    if [ -d "${git_dep}" ]; then
        git_commitHash=${github_deps[${git_dep}]}
        git_currentHash="$(git -C ${git_dep} rev-parse HEAD)"

        if [ ${git_commitHash} == "${git_currentHash}" ]; then
            continue
        fi
        git -C ${git_dep} checkout ${git_commitHash}
        continue
    else
        ./github-checkout.sh ${git_dep} ${github_deps[${git_dep}]}
    fi
done


for golang_dep in "${!golang_deps[@]}"
do
    golang_repo=$(echo ${golang_dep} | cut -d "/" -f 2)
    directory="golang.org/x/${golang_repo}"

    if [ -d "${directory}" ]; then
        golang_commitHash=${golang_deps[${golang_dep}]}
        golang_currentHash="$(git -C ${directory} rev-parse HEAD)"

        if [ ${golang_commitHash} == "${golang_currentHash}" ]; then
            continue
        fi

        git -C ${directory} checkout ${golang_commitHash}
        continue
    else
        ./golang-checkout.sh ${golang_dep} ${golang_deps[${golang_dep}]}
    fi
done
