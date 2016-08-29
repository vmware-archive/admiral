# Instruct docker daemon to trust registry certificate. See
# https://docs.docker.com/docker-trusted-registry/userguide/
# https://docs.docker.com/registry/insecure/
set -e

HOSTNAME=$1
CERT=$2

CERTS_DIR=/etc/docker/certs.d
CERT_DIR=$CERTS_DIR/$HOSTNAME
CERT_FILE=$CERT_DIR/ca.crt

mkdir -p $CERT_DIR
echo "$CERT" > $CERT_FILE

# Return a message to prevent errors while retrieving
# the response body in ShellContainerExecutorService
echo "success!";
