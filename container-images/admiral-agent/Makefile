#The image name is referenced in
#com.vmware.admiral.compute.container.ContainerDescriptionFactoryService.SHELL_AGENT_CONTAINER_DESCRIPTION_ID
# TODO: update reference when updated in ContainerDescriptionFactoryService
ADMIRAL_AGENT_IMAGE_NAME ?= vmware/admiral_agent
ADMIRAL_AGENT_IMAGE_TAR_FILENAME ?= admiral_agent
ADMIRAL_AGENT_IMAGE_VERSION ?= 0.5.0

buildgo:
	# Builds the GO application from sources inside a Linux container
	rm -rf go/bin
	docker ${DOCKER_ARGS} build --tag admiral-agent-go-image -f ./Dockerfile.buildgo .
	docker ${DOCKER_ARGS} run -v /var/run/docker.sock:/var/run/docker.sock --name admiral-agent-go admiral-agent-go-image /bin/true
	# Copies the resulting binary locally, prepared for the final agent
	docker ${DOCKER_ARGS} cp admiral-agent-go:/go/bin go
	docker ${DOCKER_ARGS} rm -f admiral-agent-go
	docker ${DOCKER_ARGS} rmi admiral-agent-go-image

buildsib:
	# Builds shellinabox from sources from github inside a Linux container
	rm -rf sib/bin
	docker ${DOCKER_ARGS} build --tag admiral-agent-sib-image -f ./sib/Dockerfile.buildsib ./sib/
	docker ${DOCKER_ARGS} run --name admiral-agent-sib admiral-agent-sib-image /bin/true
	# Copies the resulting binary locally, prepared for the final shell agent image
	docker ${DOCKER_ARGS} cp admiral-agent-sib:/shellinabox-bin/ sib/bin/
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
	docker ${DOCKER_ARGS} run -P --net=host -ti -v /var/run/docker.sock:/var/run/docker.sock --name admiral_agent $(ADMIRAL_AGENT_IMAGE_NAME):$(ADMIRAL_AGENT_IMAGE_VERSION)  /bin/sh
