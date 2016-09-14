BINARY=admiral
BINARY_WIN=admiral.exe
BUILD_PACKAGE=admiral
VERSION=0.5.0-SNAPSHOT
LDFLAGS=-ldflags "-X admiral/cmd.version=${VERSION}"

LINUX_BUILD_AMD64=env GOPATH=$(CURDIR)/.. GOOS=linux GOARCH=amd64 go build ${LDFLAGS} -o bin/linux-amd64/${BINARY} ${BUILD_PACKAGE}
LINUX_BUILD_i386=env GOPATH=$(CURDIR)/.. GOOS=linux GOARCH=386 go build ${LDFLAGS} -o bin/linux-i386/${BINARY} ${BUILD_PACKAGE}
DARWIN_BUILD_AMD64=env GOPATH=$(CURDIR)/.. GOOS=darwin GOARCH=amd64 go build ${LDFLAGS} -o bin/darwin-amd64/${BINARY} ${BUILD_PACKAGE}
DARWIN_BUILD_i386=env GOPATH=$(CURDIR)/.. GOOS=darwin GOARCH=386 go build ${LDFLAGS} -o bin/darwin-i386/${BINARY} ${BUILD_PACKAGE}
WINDOWS_BUILD_AMD64=env GOPATH=$(CURDIR)/.. GOOS=windows GOARCH=amd64 go build ${LDFLAGS} -o bin/windows-amd64/${BINARY_WIN} ${BUILD_PACKAGE}
WINDOWS_BUILD_i386=env GOPATH=$(CURDIR)/.. GOOS=windows GOARCH=386 go build ${LDFLAGS} -o bin/windows-i386/${BINARY_WIN} ${BUILD_PACKAGE}

all:
	./prepare.sh
	#linux-amd64 build
	${LINUX_BUILD_AMD64}
	#linux-i386 build
	${LINUX_BUILD_i386}
	#darwin-amd64 build
	${DARWIN_BUILD_AMD64}
	#darwin-i386 build
	${DARWIN_BUILD_i386}
	#windows-amd64 build
	${WINDOWS_BUILD_AMD64}
	#windows-i386 build
	${WINDOWS_BUILD_i386}

linux:
	./prepare.sh
	#linux-amd64 build
	${LINUX_BUILD_AMD64}
	#linux-i386 build
	${LINUX_BUILD_i386}

darwin:
	./prepare.sh
	#darwin-amd64 build
	${DARWIN_BUILD_AMD64}
	#darwin-i386 build
	${DARWIN_BUILD_i386}

windows:
	./prepare.sh
	#windows-amd64 build
	${WINDOWS_BUILD_AMD64}
	#windows-i386 build
	${WINDOWS_BUILD_i386}

