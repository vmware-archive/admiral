FROM vmware/photon-closure-runner_nodejs_base:1.0

ARG TASK_URI
ARG TOKEN
ENV TASK_URI ${TASK_URI}
ENV TOKEN ${TOKEN}

WORKDIR /app

ENTRYPOINT [ "./run.sh" ]

COPY app/*.js app/*.sh /app/
COPY app/closure_module /app/node_modules/closure_module

RUN chmod +x /app/*.sh &&\
    /app/dep_install.sh

