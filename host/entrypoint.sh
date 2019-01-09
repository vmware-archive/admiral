#!/bin/sh

set -e
set -x

if [ "$MOCK_MODE" = "true" ]
then
XENON_OPTS="$XENON_OPTS --startMockHostAdapterInstance=true"
fi

if [ "x" = "x$MEMORY_OPTS" ]
then
MEMORY_OPTS="-Xmx1280M -Xms1280M -Xss256K -Xmn256M -XX:MaxMetaspaceSize=256m"
fi

CONFIG_FILES="$DIST_CONFIG_FILE_PATH"
if [ -f $CONFIG_FILE_PATH ]
then
CONFIG_FILES="$CONFIG_FILES,$CONFIG_FILE_PATH"
fi

if [ "x" = "x$XENON_PHOTON_MODEL_PROPS" ]
then
XENON_PHOTON_MODEL_PROPS="-Dservice.document.version.retention.limit=50 -Dservice.document.version.retention.floor=10"
fi

if [ "x" = "x$XENON_ENABLE_STACKTRACE" ]
then
XENON_STACKTRACE="-Dxenon.ServiceErrorResponse.disableStackTraceCollection=true"
else
XENON_STACKTRACE=""
fi

JAVA_OPTS="$JAVA_OPTS $MEMORY_OPTS"
JAVA_OPTS="$JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$ADMIRAL_STORAGE_PATH -XX:+ExitOnOutOfMemoryError"

java -Djava.util.logging.config.file=$LOG_CONFIG_FILE_PATH -Dconfiguration.properties=$CONFIG_FILES $JAVA_OPTS -cp $ADMIRAL_ROOT/*:$ADMIRAL_ROOT/lib/*:/etc/xenon/dynamic-services/* $XENON_PHOTON_MODEL_PROPS $XENON_STACKTRACE com.vmware.admiral.host.ManagementHost --bindAddress=0.0.0.0 --port=$ADMIRAL_PORT --sandbox=$ADMIRAL_STORAGE_PATH $XENON_OPTS &
PID=$!

wait $PID