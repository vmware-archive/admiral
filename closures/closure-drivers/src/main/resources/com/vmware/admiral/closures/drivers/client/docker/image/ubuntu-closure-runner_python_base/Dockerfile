FROM ubuntu:14.04

RUN apt-get -qq -y update && \
    apt-get -qq -y install curl python3-pip pkg-config build-essential libssl-dev libffi-dev python-dev libdbus-glib-1-dev

RUN mkdir -p /app
WORKDIR /app

RUN pip3 install requests



