FROM vmware/photon-closure-runner_java_base:1.0

ARG TASK_URI
ARG TOKEN
ARG TRUST_CERTS
ENV TASK_URI ${TASK_URI}
ENV TOKEN ${TOKEN}
ENV TRUST_CERTS ${TRUST_CERTS}

WORKDIR /app

ENV PATH "$PATH:/var/opt/OpenJDK-1.8.0.112-bin/bin"

COPY app/*.* /app/
COPY app/com/vmware/admiral/closure/ /app/com/vmware/admiral/closure/

RUN /var/opt/OpenJDK-1.8.0.112-bin/bin/javac -cp \* \
/app/com/vmware/admiral/closure/runtime/Context.java \
/app/com/vmware/admiral/closure/runner/AppRunner.java \
/app/com/vmware/admiral/closure/runner/ContextImpl.java

COPY app/com/vmware/admiral/closure /app/com/vmware/admiral/closure

ENTRYPOINT ["./run.sh"]

RUN chmod +x /app/*.sh