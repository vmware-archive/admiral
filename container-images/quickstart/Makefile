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

dockerbin=$(shell which docker)

run:
	docker build --tag vmware/admiral-quickstart -f Dockerfile .
	docker run --rm --net=host -ti -v /etc/docker/:/etc/docker/ -v /var/lib/boot2docker/:/var/lib/boot2docker/ -v $(dockerbin):/bin/docker vmware/admiral-quickstart