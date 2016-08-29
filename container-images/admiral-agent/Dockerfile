FROM vmware/photon:1.0

RUN tyum install haproxy rsyslog sed gawk iproute2 procps-ng -y && tyum clean all

COPY /go/bin /agent

COPY /sib/bin/shellinaboxd /usr/local/bin/
COPY /sib/white-on-black.css /sib/terminal.png /sib/
COPY /haproxy/haproxy.cfg.template /haproxy/containers.map /haproxy/haproxy-reload.sh /haproxy/
COPY /copy-certificate.sh /
COPY /docker-ip.sh /
COPY /entrypoint.sh /
COPY /admiral_logo.txt /
COPY /conf/ /etc/

# Used by HAProxy
EXPOSE 30080
EXPOSE 30081

# Used by ShellInABox
EXPOSE 4200

ENTRYPOINT ["/bin/bash", "/entrypoint.sh"]