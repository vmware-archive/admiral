FROM ubuntu:14.04

RUN apt-get update && \
    apt-get -qq -y install --no-install-recommends wget curl
RUN wget http://nodejs.org/dist/v4.3.0/node-v4.3.0-linux-x64.tar.gz && \
    tar -C /usr/local --strip-components 1 -xzf node-v4.3.0-linux-x64.tar.gz && \
    mkdir -p /app/closure_module

WORKDIR /app

COPY app/closure_module/package.json /app/closure_module/package.json

RUN npm install /app/closure_module --production --registry=http://o11n-registry.adlbg.eng.vmware.com/npmjs && \
    rm -fr ~/.npm

