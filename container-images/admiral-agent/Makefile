include agent.properties

buildgo:
	# Builds the GO application from sources inside a Linux container
	rm -rf tocopy/agent
	docker ${DOCKER_ARGS} build --tag admiral-agent-go-image -f ./Dockerfile.buildgo .
	docker ${DOCKER_ARGS} run -v /var/run/docker.sock:/var/run/docker.sock --name admiral-agent-go admiral-agent-go-image /bin/true
	# Copies the resulting binary locally, prepared for the final agent
	docker ${DOCKER_ARGS} cp admiral-agent-go:/go/bin/ tocopy/agent/
	docker ${DOCKER_ARGS} rm -f admiral-agent-go
	docker ${DOCKER_ARGS} rmi admiral-agent-go-image

buildsib:
	# Builds shellinabox from sources from github inside a Linux container
	rm -rf tocopy/usr
	mkdir -p tocopy/usr/local/
	docker ${DOCKER_ARGS} build --tag admiral-agent-sib-image -f ./Dockerfile.buildsib .
	docker ${DOCKER_ARGS} run --name admiral-agent-sib admiral-agent-sib-image /bin/true
	# Copies the resulting binary locally, prepared for the final shell agent image
	docker ${DOCKER_ARGS} cp admiral-agent-sib:/shellinabox-bin/ tocopy/usr/local/bin/
	docker ${DOCKER_ARGS} rm -f admiral-agent-sib
	docker ${DOCKER_ARGS} rmi admiral-agent-sib-image

buildagent:
	docker ${DOCKER_ARGS} build --tag $(ADMIRAL_AGENT_IMAGE_NAME):$(ADMIRAL_AGENT_IMAGE_VERSION) -f Dockerfile .
	mkdir -p ../../host/images-bin || echo "Image dir exists?"
	docker ${DOCKER_ARGS} save $(ADMIRAL_AGENT_IMAGE_NAME):$(ADMIRAL_AGENT_IMAGE_VERSION) > ../../host/images-bin/$(ADMIRAL_AGENT_IMAGE_TAR_FILENAME).tar

buildall:
	make buildsib
	make buildgo
	make buildagent

run:
	docker ${DOCKER_ARGS} run -P -ti -v /var/run/docker.sock:/var/run/docker.sock --name admiral_agent $(ADMIRAL_AGENT_IMAGE_NAME):$(ADMIRAL_AGENT_IMAGE_VERSION)  /bin/sh
