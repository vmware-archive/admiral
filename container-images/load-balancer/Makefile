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

include agent.properties

ADMIRAL_LOAD_BALANCER_TAR_FILENAME=load_balancer
FOLDER_HASH	:= $(shell pwd | sha1sum | head -c 7)
#GIT_HASH	:= $(git log  --pretty=format:%h -n 1 .)
GIT_HASH	= 756asd23
TAG         := $(FOLDER_HASH)-$(GIT_HASH)

buildall: buildloadbalancer

prepare:
	rm -fr files/usr/bin/*

buildgo: prepare
	docker ${DOCKER_ARGS} rm -f admiral-load-balancer-go-$(TAG) || true
	# Builds the GO application from sources inside a Linux container
	docker ${DOCKER_ARGS} build ${BUILD_ARGS} --tag admiral-load-balancer-go-image-$(TAG) -f ./Dockerfile.buildgo .
	docker ${DOCKER_ARGS} run -v /var/run/docker.sock:/var/run/docker.sock --name admiral-load-balancer-go-$(TAG) admiral-load-balancer-go-image-$(TAG) /bin/true
	# Copies the resulting binary locally, prepared for the final agent
	rm -fr files/usr/bin
	mkdir -p files/usr/bin
	docker ${DOCKER_ARGS} cp admiral-load-balancer-go-$(TAG):/go/src/proxy-config files/usr/bin/
	docker ${DOCKER_ARGS} rm -f admiral-load-balancer-go-$(TAG)

buildimage:
	docker ${DOCKER_ARGS} build ${BUILD_ARGS} --tag $(ADMIRAL_LOAD_BALANCER_NAME):$(ADMIRAL_LOAD_BALANCER_VERSION) -f Dockerfile .
	docker ${DOCKER_ARGS} save $(ADMIRAL_LOAD_BALANCER_NAME):$(ADMIRAL_LOAD_BALANCER_VERSION) \
		| xz -z -f -c > $(ADMIRAL_LOAD_BALANCER_TAR_FILENAME)-export-$(TAG)

buildloadbalancer: buildgo buildimage
	rm -f ../../host/images-bin/$(ADMIRAL_LOAD_BALANCER_TAR_FILENAME)*
	mkdir -p ../../host/images-bin || true
	cp -l $(ADMIRAL_LOAD_BALANCER_TAR_FILENAME)-export-$(TAG) \
		../../host/images-bin/$(ADMIRAL_LOAD_BALANCER_TAR_FILENAME).tar.xz

clean:
	docker rmi admiral-load-balancer-go-image-$(TAG) || true
	rm -fr $(ADMIRAL_LOAD_BALANCER_TAR_FILENAME)-export-*
	rm -fr ../../host/images-bin/$(ADMIRAL_LOAD_BALANCER_TAR_FILENAME)*.*
	rm -fr files/usr/bin/*

run:
	docker ${DOCKER_ARGS} kill load_balancer || true
	docker ${DOCKER_ARGS} rm load_balancer || true
	docker ${DOCKER_ARGS} run --rm -P -ti -v /var/run/docker.sock:/var/run/docker.sock --name load_balancer $(ADMIRAL_LOAD_BALANCER_NAME):$(ADMIRAL_LOAD_BALANCER_VERSION)
