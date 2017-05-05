# Copyright (c) 2016 VMware, Inc. All Rights Reserved.
#
# This product is licensed to you under the Apache License, Version 2.0 (the "License").
# You may not use this product except in compliance with the License.
#
# This product may include a number of subcomponents with separate copyright notices
# and license terms. Your use of these subcomponents is subject to the terms and
# conditions of the subcomponent's license, as noted in the LICENSE file.

BUILD_PACKAGE	:=admiral
VERSION 		:=$(shell perl -lne 'print "$$&" if /(?<=<version>).*?(?=<\/version>)/' ../../pom.xml | head -1)
LDFLAGS         :=-ldflags "-X admiral/cmd.version=$(VERSION)"
GOPATH			:=$(CURDIR)/..


all: linux darwin windows

prepare:
	GOPATH=$(GOPATH) go env

linux: prepare
	GOPATH=$(GOPATH) GOOS=linux GOARCH=amd64   go install $(LDFLAGS) $(BUILD_PACKAGE)
	GOPATH=$(GOPATH) GOOS=linux GOARCH=386     go install $(LDFLAGS) $(BUILD_PACKAGE)

darwin: prepare
	GOPATH=$(GOPATH) GOOS=darwin GOARCH=amd64  go install $(LDFLAGS) $(BUILD_PACKAGE)
	GOPATH=$(GOPATH) GOOS=darwin GOARCH=386    go install $(LDFLAGS) $(BUILD_PACKAGE)

windows: prepare
	GOPATH=$(GOPATH) GOOS=windows GOARCH=amd64 go install $(LDFLAGS) $(BUILD_PACKAGE)
	GOPATH=$(GOPATH) GOOS=windows GOARCH=386   go install $(LDFLAGS) $(BUILD_PACKAGE)

test: prepare
	GOPATH=$(GOPATH) go test -v -p=1 -tags=integration admiral/...
	GOPATH=$(GOPATH) go test -v -p=1 -tags=e2e admiral/...

clean:
	rm -rf ../bin ../pkg