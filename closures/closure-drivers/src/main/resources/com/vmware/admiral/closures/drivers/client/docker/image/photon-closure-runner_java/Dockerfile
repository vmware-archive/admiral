FROM vmware/photon-closure-runner_java_base:1.0

ARG TASK_URI
ARG TOKEN
ARG TRUST_CERTS
ENV TASK_URI ${TASK_URI}
ENV TOKEN ${TOKEN}
ENV TRUST_CERTS ${TRUST_CERTS}

WORKDIR /app

COPY app/*.* /app/
COPY app/com/vmware/admiral/closure /app/com/vmware/admiral/closure

ENTRYPOINT ["./run.sh"]

RUN chmod +x /app/*.sh