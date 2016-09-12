FROM vmware/photon:1.0

RUN tyum install gawk procps-ng -y && tyum clean all

COPY /tocopy /

# Used by ShellInABox
EXPOSE 4200

ENTRYPOINT ["/bin/bash", "/entrypoint.sh"]