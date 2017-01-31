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


build_nodejsbase:
	# Builds closure NodeJS runner base image
	docker ${DOCKER_ARGS} build --force-rm -t $(ADMIRAL_CLOSURE_NODEJS_IMAGE_NAME):$(ADMIRAL_CLOSURE_NODEJS_IMAGE_VERSION) photon-closure-runner_nodejs_base
	mkdir -p ../../host/images-bin || echo "Image dir exists?"
	docker ${DOCKER_ARGS} save $(ADMIRAL_CLOSURE_NODEJS_IMAGE_NAME):$(ADMIRAL_CLOSURE_NODEJS_IMAGE_VERSION) | xz -z	-f -c > \
	 ../../host/images-bin/$(ADMIRAL_CLOSURE_NODEJS_IMAGE_TAR_FILENAME).tar.xz

build_pythonbase:
	# Builds closure Python runner base image
	docker ${DOCKER_ARGS} build --force-rm -t $(ADMIRAL_CLOSURE_PYTHON_IMAGE_NAME):$(ADMIRAL_CLOSURE_PYTHON_IMAGE_VERSION) photon-closure-runner_python_base
	mkdir -p ../../host/images-bin || echo "Image dir exists?"
	docker ${DOCKER_ARGS} save $(ADMIRAL_CLOSURE_PYTHON_IMAGE_NAME):$(ADMIRAL_CLOSURE_PYTHON_IMAGE_VERSION) | xz -z	-f -c > \
	../../host/images-bin/$(ADMIRAL_CLOSURE_PYTHON_IMAGE_TAR_FILENAME).tar.xz

buildall:
	make build_nodejsbase
	make build_pythonbase