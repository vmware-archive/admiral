FROM vmware/photon-closure-runner_python_base:2.0

ARG TASK_URI
ARG TOKEN
ARG TRUST_CERTS
ENV TASK_URI ${TASK_URI}
ENV TOKEN ${TOKEN}
ENV TRUST_CERTS ${TRUST_CERTS}

ENV PIP_DEFAULT_TIMEOUT=120

COPY app/*.* /app/

ENTRYPOINT ["./run.sh"]
RUN chmod +x /app/*.sh
RUN /app/dep_install.sh

