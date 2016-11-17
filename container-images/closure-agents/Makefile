include agent.properties


build_nodejsbase:
	# Builds closure Nodejs runner base image
	docker ${DOCKER_ARGS} build --force-rm -t $(ADMIRAL_CLOSURE_NODEJS_IMAGE_NAME):$(ADMIRAL_CLOSURE_NODEJS_IMAGE_VERSION) photon-closure-runner_nodejs_4.3.0_base
	mkdir -p ../../host/images-bin || echo "Image dir exists?"
	docker ${DOCKER_ARGS} save $(ADMIRAL_CLOSURE_NODEJS_IMAGE_NAME):$(ADMIRAL_CLOSURE_NODEJS_IMAGE_VERSION) | xz > \
	 ../../host/images-bin/$(ADMIRAL_CLOSURE_NODEJS_IMAGE_TAR_FILENAME).xz

build_pythonbase:
	# Builds closure Python runner base image
	docker ${DOCKER_ARGS} build --force-rm -t $(ADMIRAL_CLOSURE_PYTHON_IMAGE_NAME):$(ADMIRAL_CLOSURE_PYTHON_IMAGE_VERSION) photon-closure-runner_python_3.4.3_base
	mkdir -p ../../host/images-bin || echo "Image dir exists?"
	docker ${DOCKER_ARGS} save $(ADMIRAL_CLOSURE_PYTHON_IMAGE_NAME):$(ADMIRAL_CLOSURE_PYTHON_IMAGE_VERSION) | xz > \
	../../host/images-bin/$(ADMIRAL_CLOSURE_PYTHON_IMAGE_TAR_FILENAME).xz

buildall:
	make build_nodejsbase
	make build_pythonbase