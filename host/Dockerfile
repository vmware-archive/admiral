FROM vmware/admiral-base

ENTRYPOINT ["/entrypoint.sh"]

COPY entrypoint.sh /entrypoint.sh
COPY configuration.properties $DIST_CONFIG_FILE_PATH
COPY logging.properties $LOG_CONFIG_FILE_PATH

COPY images-bin/* $USER_RESOURCES
COPY target/lib target/admiral-host-*.jar $ADMIRAL_ROOT/
