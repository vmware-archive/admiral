FROM vmware/photon:1.0

MAINTAINER Admiral Team, https://vmware.github.io/admiral/

RUN tyum install -y openjre.x86_64 && \
	tyum clean all && \
	mkdir -p /usr/lib/jvm && \
	export JAVA_HOME="/usr/lib/jvm/default-jvm" && \
	ln -s /var/opt/OpenJDK-1.8.0.92-bin $JAVA_HOME && \
	ln -s $JAVA_HOME/bin/* /usr/bin/

ENV ADMIRAL_PORT=8282 \
   ADMIRAL_STORAGE_PATH=/var/admiral/ \
   USER_RESOURCES=/etc/xenon/user-resources/system-images/ \
   ADMIRAL_ROOT=/admiral \
   MOCK_MODE=false

EXPOSE $ADMIRAL_PORT
VOLUME $ADMIRAL_STORAGE_PATH

RUN mkdir $ADMIRAL_ROOT && mkdir -p $USER_RESOURCES

COPY target/lib target/admiral-host-0.5.0.jar $ADMIRAL_ROOT/

COPY images-bin/* $USER_RESOURCES

COPY entrypoint.sh /entrypoint.sh

RUN chmod 700 /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]