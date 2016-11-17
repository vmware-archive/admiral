FROM vmware/photon:1.0

MAINTAINER Admiral Team, https://vmware.github.io/admiral/

RUN tdnf install -y python3-devel openssl-devel libffi dbus-glib dbus-glib python3 curl

RUN mkdir -p /app
WORKDIR /app

RUN pip3 install requests



