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
include agent.properties

ADMIRAL_AGENT_IMAGE_TAR_FILENAME=admiral_agent
FOLDER_HASH	:= $(shell pwd | sha1sum | head -c 7)
GIT_HASH	:= $(shell git log  --pretty=format:%h -n 1 .)
TAG         := $(FOLDER_HASH)-$(GIT_HASH)

buildall: buildagent

$(ADMIRAL_AGENT_IMAGE_TAR_FILENAME)-export-$(TAG):
	docker ${DOCKER_ARGS} build ${BUILD_ARGS} --tag $(ADMIRAL_AGENT_IMAGE_NAME):$(ADMIRAL_AGENT_IMAGE_VERSION) -f Dockerfile .
	docker ${DOCKER_ARGS} save $(ADMIRAL_AGENT_IMAGE_NAME):$(ADMIRAL_AGENT_IMAGE_VERSION) \
		| xz -z -f -c > $(ADMIRAL_AGENT_IMAGE_TAR_FILENAME)-export-$(TAG)


buildagent: $(ADMIRAL_AGENT_IMAGE_TAR_FILENAME)-export-$(TAG)
	rm -f ../../host/images-bin/$(ADMIRAL_AGENT_IMAGE_TAR_FILENAME)*
	mkdir -p ../../host/images-bin || true
	cp -l $(ADMIRAL_AGENT_IMAGE_TAR_FILENAME)-export-$(TAG) \
		../../host/images-bin/$(ADMIRAL_AGENT_IMAGE_TAR_FILENAME).tar.xz

clean:
	docker rmi admiral-agent-go-image-$(TAG) || true
	rm -fr $(ADMIRAL_AGENT_IMAGE_TAR_FILENAME)-export-*

run:
	docker ${DOCKER_ARGS} rm --force --volumes admiral_agent || true
	docker ${DOCKER_ARGS} run --rm -P -ti --name admiral_agent $(ADMIRAL_AGENT_IMAGE_NAME):$(ADMIRAL_AGENT_IMAGE_VERSION)
