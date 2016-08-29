buildgo:
	rm -rf go/bin
	docker build --tag cmp-test-identity-go-image -f ./Dockerfile.buildgo .
	docker run --name cmp-test-identity-go cmp-test-identity-go-image /bin/true
	docker cp cmp-test-identity-go:/go/bin go
	docker rm -f cmp-test-identity-go
	docker rmi cmp-test-identity-go-image

buildidentity:
	docker build --tag cmp/cmp_test_identity:latest -f Dockerfile .