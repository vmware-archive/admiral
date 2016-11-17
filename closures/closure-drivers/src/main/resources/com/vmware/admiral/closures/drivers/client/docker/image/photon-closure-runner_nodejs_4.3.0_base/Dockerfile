FROM vmware/photon:1.0

MAINTAINER Admiral Team, https://vmware.github.io/admiral/

RUN tdnf -y install wget curl npm tar gzip
RUN wget http://nodejs.org/dist/v4.3.0/node-v4.3.0-linux-x64.tar.gz &&\
    tar --strip-components 1 -xzvf node-v4.3.0-linux-x64.tar.gz -C /usr/local &&\
    mkdir -p /app/closure_module

WORKDIR /app

COPY app/closure_module/package.json /app/closure_module/package.json

RUN npm install /app/closure_module --production --registry=http://o11n-registry.adlbg.eng.vmware.com/npmjs && \
    rm -fr ~/.npm

