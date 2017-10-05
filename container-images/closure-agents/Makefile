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

ADMIRAL_CLOSURE_NODEJS_IMAGE_TAR_FILENAME=photon-closure-runner_nodejs_base
ADMIRAL_CLOSURE_PYTHON_IMAGE_TAR_FILENAME=photon-closure-runner_python_base
ADMIRAL_CLOSURE_JAVA_IMAGE_TAR_FILENAME=photon-closure-runner_java_base
ADMIRAL_CLOSURE_POWERSHELL_IMAGE_TAR_FILENAME=photon-closure-runner_powershell_base

FOLDER_HASH	:= $(shell pwd | sha1sum | head -c 7)
GIT_HASH	:= $(shell git log  --pretty=format:%h -n 1 .)
TAG         := $(FOLDER_HASH)-$(GIT_HASH)

buildall: build_nodejsbase build_pythonbase build_javabase build_powershellbase

build_nodejsbase: $(ADMIRAL_CLOSURE_NODEJS_IMAGE_TAR_FILENAME)-export-$(TAG)
	rm -f ../../host/images-bin/$(ADMIRAL_CLOSURE_NODEJS_IMAGE_TAR_FILENAME).tar.xz
	mkdir -p ../../host/images-bin || true
	cp $(ADMIRAL_CLOSURE_NODEJS_IMAGE_TAR_FILENAME)-export-$(TAG) \
	  ../../host/images-bin/$(ADMIRAL_CLOSURE_NODEJS_IMAGE_TAR_FILENAME).tar.xz

build_pythonbase: $(ADMIRAL_CLOSURE_PYTHON_IMAGE_TAR_FILENAME)-export-$(TAG)
	rm -f ../../host/images-bin/$(ADMIRAL_CLOSURE_PYTHON_IMAGE_TAR_FILENAME).tar.xz
	mkdir -p ../../host/images-bin || true
	cp $(ADMIRAL_CLOSURE_PYTHON_IMAGE_TAR_FILENAME)-export-$(TAG) \
	  ../../host/images-bin/$(ADMIRAL_CLOSURE_PYTHON_IMAGE_TAR_FILENAME).tar.xz

build_javabase: $(ADMIRAL_CLOSURE_JAVA_IMAGE_TAR_FILENAME)-export-$(TAG)
	rm -f ../../host/images-bin/$(ADMIRAL_CLOSURE_JAVA_IMAGE_TAR_FILENAME).tar.xz
	mkdir -p ../../host/images-bin || true
	cp $(ADMIRAL_CLOSURE_JAVA_IMAGE_TAR_FILENAME)-export-$(TAG) \
	  ../../host/images-bin/$(ADMIRAL_CLOSURE_JAVA_IMAGE_TAR_FILENAME).tar.xz

build_powershellbase: $(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_TAR_FILENAME)-export-$(TAG)
	rm -f ../../host/images-bin/$(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_TAR_FILENAME).tar.xz
	mkdir -p ../../host/images-bin || true
	cp $(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_TAR_FILENAME)-export-$(TAG) \
	  ../../host/images-bin/$(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_TAR_FILENAME).tar.xz

$(ADMIRAL_CLOSURE_NODEJS_IMAGE_TAR_FILENAME)-export-$(TAG):
	docker ${DOCKER_ARGS} build \
	    -t $(ADMIRAL_CLOSURE_NODEJS_IMAGE_NAME):$(ADMIRAL_CLOSURE_NODEJS_IMAGE_VERSION) \
	    photon-closure-runner_nodejs_base
	docker ${DOCKER_ARGS} save \
	    $(ADMIRAL_CLOSURE_NODEJS_IMAGE_NAME):$(ADMIRAL_CLOSURE_NODEJS_IMAGE_VERSION) \
	    | xz -z -f -c > $(ADMIRAL_CLOSURE_NODEJS_IMAGE_TAR_FILENAME)-export-$(TAG)

$(ADMIRAL_CLOSURE_PYTHON_IMAGE_TAR_FILENAME)-export-$(TAG):
	docker ${DOCKER_ARGS} build \
	    -t $(ADMIRAL_CLOSURE_PYTHON_IMAGE_NAME):$(ADMIRAL_CLOSURE_PYTHON_IMAGE_VERSION) \
	    photon-closure-runner_python_base
	docker ${DOCKER_ARGS} save \
	    $(ADMIRAL_CLOSURE_PYTHON_IMAGE_NAME):$(ADMIRAL_CLOSURE_PYTHON_IMAGE_VERSION) \
	    | xz -z -f -c > $(ADMIRAL_CLOSURE_PYTHON_IMAGE_TAR_FILENAME)-export-$(TAG)

$(ADMIRAL_CLOSURE_JAVA_IMAGE_TAR_FILENAME)-export-$(TAG):
	docker ${DOCKER_ARGS} build \
	    -t $(ADMIRAL_CLOSURE_JAVA_IMAGE_NAME):$(ADMIRAL_CLOSURE_JAVA_IMAGE_VERSION) \
	    photon-closure-runner_java_base
	docker ${DOCKER_ARGS} save \
	    $(ADMIRAL_CLOSURE_JAVA_IMAGE_NAME):$(ADMIRAL_CLOSURE_JAVA_IMAGE_VERSION) \
	    | xz -z -f -c > $(ADMIRAL_CLOSURE_JAVA_IMAGE_TAR_FILENAME)-export-$(TAG)

$(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_TAR_FILENAME)-export-$(TAG):
	docker ${DOCKER_ARGS} build \
	    -t $(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_NAME):$(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_VERSION) \
	    photon-closure-runner_powershell_base
	docker ${DOCKER_ARGS} save \
	    $(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_NAME):$(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_VERSION) \
	    | xz -z -f -c > $(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_TAR_FILENAME)-export-$(TAG)

clean:
	docker rmi \
	   $(ADMIRAL_CLOSURE_NODEJS_IMAGE_NAME):$(ADMIRAL_CLOSURE_NODEJS_IMAGE_VERSION) \
	   $(ADMIRAL_CLOSURE_PYTHON_IMAGE_NAME):$(ADMIRAL_CLOSURE_PYTHON_IMAGE_VERSION) \
	   $(ADMIRAL_CLOSURE_JAVA_IMAGE_NAME):$(ADMIRAL_CLOSURE_JAVA_IMAGE_VERSION) \
	   $(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_NAME):$(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_VERSION) \
	   || true

	rm -fr $(ADMIRAL_CLOSURE_NODEJS_IMAGE_TAR_FILENAME)-export-*
	rm -fr $(ADMIRAL_CLOSURE_PYTHON_IMAGE_TAR_FILENAME)-export-*
	rm -fr $(ADMIRAL_CLOSURE_JAVA_IMAGE_TAR_FILENAME)-export-*
	rm -fr $(ADMIRAL_CLOSURE_POWERSHELL_IMAGE_TAR_FILENAME)-export-*
	rm -fr ../../host/images-bin/photon-closure-runner*
