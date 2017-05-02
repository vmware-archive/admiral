#!/bin/sh

set -e
set -x

if [ "$MOCK_MODE" = "true" ]
then
XENON_OPTS="$XENON_OPTS --startMockHostAdapterInstance=true"
fi

if [ "x" = "x$MEMORY_OPTS" ]
then
MEMORY_OPTS="-Xmx768M -Xms768M -Xss256K -Xmn256M -XX:MaxMetaspaceSize=256m"
fi

CONFIG_FILES="$DIST_CONFIG_FILE_PATH"
if [ -f $CONFIG_FILE_PATH ]
then
CONFIG_FILES="$CONFIG_FILES,$CONFIG_FILE_PATH"
fi


JAVA_OPTS="$JAVA_OPTS $MEMORY_OPTS"
JAVA_OPTS="$JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError"

java -Dconfiguration.properties=$CONFIG_FILES $JAVA_OPTS -cp $ADMIRAL_ROOT/*:$ADMIRAL_ROOT/lib/*:/etc/xenon/dynamic-services/* com.vmware.admiral.host.ManagementHost --bindAddress=0.0.0.0 --port=$ADMIRAL_PORT --sandbox=$ADMIRAL_STORAGE_PATH $XENON_OPTS &
PID=$!

wait $PID