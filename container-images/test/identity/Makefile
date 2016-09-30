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

buildgo:
	rm -rf go/bin
	docker build --tag cmp-test-identity-go-image -f ./Dockerfile.buildgo .
	docker run --name cmp-test-identity-go cmp-test-identity-go-image /bin/true
	docker cp cmp-test-identity-go:/go/bin go
	docker rm -f cmp-test-identity-go
	docker rmi cmp-test-identity-go-image

buildidentity:
	docker build --tag cmp/cmp_test_identity:latest -f Dockerfile .