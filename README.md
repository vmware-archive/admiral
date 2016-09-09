![Admiral](https://vmware.github.io/admiral/assets/img/admiral.png "VMware Admiral")

# Admiral

###Contents
- [What is Admiral](#what-is-admiral)
- [Getting started](#getting-started)
- [Contributing](#contributing)
- [License](#license)

## What is Admiral?

Admiral™ is a highly scalable and very lightweight Container Management platform for deploying and managing container based applications. It is designed to have a small footprint and boot extremely quickly. Admiral™ is intended to provide automated deployment and lifecycle management of containers.

This container management solution can help reduce complexity and achieve advantages including simplified and automated application delivery, optimized resource utilization along with business governance and applying business policies and overall data center integration.

Admiral is a service written in Java and based on VMware's [Xenon framework](https://github.com/vmware/xenon/). This service enables the users to:
- manage Docker hosts, where containers will be deployed
- manage Policies (together with Resource Pools, Deployment Policies, etc.), to establish the preferences about what host(s) a container deployment will actually use
- manage Templates (including one or more container images) and Docker Registries
- manage Containers and Applications
- manage other common and required entities like credentials, certificates, etc.

## Getting started

### Running Admiral

There are three ways you can start Admiral:

#### 1. Run container image

```shell
docker run -d -p 8282:8282 --name admiral vmware/admiral
```
Open `http://<docker-host-IP>:8282` in browser...[Configure Docker Host](https://github.com/vmware/admiral/wiki/User-guide#configure-existing-container-docker-host)

#### 2. Download the published build archive, you can find it in 'Downloads' section [here](https://bintray.com/vmware/admiral/admiral).

```shell
java -jar admiral-host-*-uber-jar-with-agent.jar --bindAddress=0.0.0.0 --port=8282
```
Open `http://127.0.0.1:8282` in browser...[Configure Docker Host](https://github.com/vmware/admiral/wiki/User-guide#configure-existing-container-docker-host)

#### 3. Clone the repo and build locally. Detailed instructions about building locally can be found in the [Admiral developer guide](https://github.com/vmware/admiral/wiki/Developer-Guide):

* Building the Admiral agent first
```shell
cd container-images/admiral-agent
make buildall
```

* Building the Java project
```shell
mvn clean install -DskipTests
```

* Run the project
java -jar host/target/admiral-host-*-jar-with-dependencies-and-agent.jar --bindAddress=0.0.0.0 --port=8282

Open `http://127.0.0.1:8282` in browser...[Configure Docker Host](https://github.com/vmware/admiral/wiki/User-guide#configure-existing-container-docker-host) 

### Building the code

```shell
mvn clean install
```

More info on [Admiral wiki](https://github.com/vmware/admiral/wiki)

## Contributing

You are invited to contribute new features, fixes, or updates, large or small; we are always thrilled to receive pull requests, and do our best to process them as fast as we can. If you wish to contribute code and you have not signed our contributor license agreement (CLA), our bot will update the issue when you open a [Pull Request](https://help.github.com/articles/creating-a-pull-request). For any questions about the CLA process, please refer to our [FAQ](https://cla.vmware.com/faq).

Before you start to code, we recommend discussing your plans through a  [GitHub issue](https://github.com/vmware/admiral/issues) or discuss it first with the official project maintainers via the [gitter.im chat](https://gitter.im/project-admiral/Lobby), especially for more ambitious contributions. This gives other contributors a chance to point you in the right direction, give you feedback on your design, and help you find out if someone else is working on the same thing.

## License

Admiral is available under the [Apache 2 license](LICENSE).

This project uses open source components which have additional licensing terms.  The source files / docker images and licensing terms for these open source components can be found at the following locations:

- Photon OS [docker image](https://hub.docker.com/_/photon/), [license](https://github.com/vmware/photon/blob/master/COPYING)
- Shell in a box [sources](https://github.com/shellinabox), [license](https://github.com/shellinabox/shellinabox/blob/master/GPL-2)


[Admiral wiki](https://github.com/vmware/admiral/wiki)
