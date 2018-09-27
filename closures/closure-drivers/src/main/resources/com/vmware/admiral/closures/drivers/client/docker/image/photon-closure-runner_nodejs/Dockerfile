FROM vmware/photon-closure-runner_nodejs_base:2.0

ARG TASK_URI
ARG TOKEN
ARG TRUST_CERTS
ENV TASK_URI ${TASK_URI}
ENV TOKEN ${TOKEN}
ENV TRUST_CERTS ${TRUST_CERTS}

WORKDIR /app

ENTRYPOINT [ "./run.sh" ]

COPY app/*.js app/*.sh /app/
COPY app/closure_module /app/node_modules/closure_module

RUN chmod +x /app/*.sh
RUN /app/dep_install.sh

