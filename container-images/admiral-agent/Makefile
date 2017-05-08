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
include agent.properties

ADMIRAL_AGENT_IMAGE_TAR_FILENAME=admiral_agent
FOLDER_HASH	:= $(shell pwd | sha1sum | head -c 7)
GIT_HASH	:= $(shell git log  --pretty=format:%h -n 1 .)
TAG         := $(FOLDER_HASH)-$(GIT_HASH)

buildall: buildagent

prepare:
	rm -fr tocopy/agent/*

buildgo: prepare
	docker ${DOCKER_ARGS} rm -f admiral-agent-go-$(TAG) || true
	# Builds the GO application from sources inside a Linux container
	docker ${DOCKER_ARGS} build ${BUILD_ARGS} --tag admiral-agent-go-image-$(TAG) -f ./Dockerfile.buildgo .
	docker ${DOCKER_ARGS} run -v /var/run/docker.sock:/var/run/docker.sock --name admiral-agent-go-$(TAG) admiral-agent-go-image-$(TAG) /bin/true
	# Copies the resulting binary locally, prepared for the final agent
	rm -fr tocopy/agent
	docker ${DOCKER_ARGS} cp admiral-agent-go-$(TAG):/go/bin/ tocopy/agent/
	docker ${DOCKER_ARGS} rm -f admiral-agent-go-$(TAG)

buildsib: prepare
	# Builds shellinabox from sources from github inside a Linux container
	docker ${DOCKER_ARGS} rm -f admiral-agent-sib-$(TAG) || true
	rm -fr tocopy/usr
	mkdir -p tocopy/usr/local/
	docker ${DOCKER_ARGS} build ${BUILD_ARGS} --tag admiral-agent-sib-image-$(TAG) -f ./Dockerfile.buildsib .
	docker ${DOCKER_ARGS} run --name admiral-agent-sib-$(TAG) admiral-agent-sib-image-$(TAG) /bin/true
	# Copies the resulting binary locally, prepared for the final shell agent image
	docker ${DOCKER_ARGS} cp admiral-agent-sib-$(TAG):/shellinabox-bin/ tocopy/usr/local/bin/
	docker ${DOCKER_ARGS} rm -f admiral-agent-sib-$(TAG)

$(ADMIRAL_AGENT_IMAGE_TAR_FILENAME)-export-$(TAG):
	docker ${DOCKER_ARGS} build ${BUILD_ARGS} --tag $(ADMIRAL_AGENT_IMAGE_NAME):$(ADMIRAL_AGENT_IMAGE_VERSION) -f Dockerfile .
	docker ${DOCKER_ARGS} save $(ADMIRAL_AGENT_IMAGE_NAME):$(ADMIRAL_AGENT_IMAGE_VERSION) \
		| xz -z -f -c > $(ADMIRAL_AGENT_IMAGE_TAR_FILENAME)-export-$(TAG)


buildagent: buildgo buildsib $(ADMIRAL_AGENT_IMAGE_TAR_FILENAME)-export-$(TAG)
	rm -f ../../host/images-bin/$(ADMIRAL_AGENT_IMAGE_TAR_FILENAME)*
	mkdir -p ../../host/images-bin || true
	cp -l $(ADMIRAL_AGENT_IMAGE_TAR_FILENAME)-export-$(TAG) \
		../../host/images-bin/$(ADMIRAL_AGENT_IMAGE_TAR_FILENAME).tar.xz

clean:
	docker rmi admiral-agent-go-image-$(TAG) || true
	docker rmi admiral-agent-sib-image-$(TAG) || true
	rm -fr $(ADMIRAL_AGENT_IMAGE_TAR_FILENAME)-export-*

run:
	docker ${DOCKER_ARGS} kill admiral_agent || true
	docker ${DOCKER_ARGS} rm admiral_agent || true
	docker ${DOCKER_ARGS} run --rm -P -ti -v /var/run/docker.sock:/var/run/docker.sock --name admiral_agent $(ADMIRAL_AGENT_IMAGE_NAME):$(ADMIRAL_AGENT_IMAGE_VERSION)
