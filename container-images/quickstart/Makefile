dockerbin=$(shell which docker)

run:
	docker build --tag vmware/admiral-quickstart -f Dockerfile .
	docker run --rm --net=host -ti -v /etc/docker/:/etc/docker/ -v /var/lib/boot2docker/:/var/lib/boot2docker/ -v $(dockerbin):/bin/docker vmware/admiral-quickstart