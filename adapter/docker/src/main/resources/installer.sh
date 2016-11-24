# This script needs to be executed as root or using sudo

PORT=$1

if [ "x$PORT" == "x" ]
then
	PORT=2376
fi

RELEASE=$(cat /etc/*-release)
echo $RELEASE | grep CoreOS
if [ $? -eq 0 ]
then
	echo "Installing on CoreOS"
	mkdir -p /etc/docker || echo "Already exists?"
	cp -f certs/server.pem /etc/docker/
	cp -f certs/server-key.pem /etc/docker/
	cp -f certs/ca.pem /etc/docker/
	mkdir -p /etc/systemd/system/docker.service.d/
	CONTENT="[Service]\nEnvironment=\"DOCKER_OPTS=-H 0.0.0.0:$PORT --tlsverify --tlscacert=/etc/docker/ca.pem --tlscert=/etc/docker/server.pem --tlskey=/etc/docker/server-key.pem\""
	echo -e $CONTENT > /etc/systemd/system/docker.service.d/docker.conf
	systemctl daemon-reload
	systemctl enable docker
	systemctl restart docker
fi

echo $RELEASE | grep Ubuntu
if [ $? -eq 0 ]
then
	echo "Installing on Ubuntu"
	wget -O get_docker.sh https://get.docker.com/
	sh get_docker.sh
	groupadd docker || echo "Already exists?"
	gpasswd -a administrator docker
	gpasswd -a $USER docker

	cp -f certs/server.pem /etc/docker/
	cp -f certs/server-key.pem /etc/docker/
	cp -f certs/ca.pem /etc/docker/

	sed -i "s@ExecStart=.*\$@ExecStart=/usr/bin/dockerd --storage-driver=devicemapper -H fd:// -H=0.0.0.0:$PORT --tlsverify --tlscacert=/etc/docker/ca.pem --tlscert=/etc/docker/server.pem --tlskey=/etc/docker/server-key.pem@" /lib/systemd/system/docker.service
	systemctl daemon-reload
	service docker restart
fi

echo $RELEASE | grep Photon
if [ $? -eq 0 ]
then
	echo "Installing on PhotonOS"
	mkdir -p /etc/docker || echo "Already exists?"
	cp -f certs/server.pem /etc/docker/
	cp -f certs/server-key.pem /etc/docker/
	cp -f certs/ca.pem /etc/docker/

	echo DOCKER_OPTS=\"-H 0.0.0.0:$PORT -H unix:///var/run/docker.sock --tlsverify --tlscacert=/etc/docker/ca.pem --tlscert=/etc/docker/server.pem --tlskey=/etc/docker/server-key.pem\" > /etc/default/docker
	iptables -A INPUT -p tcp --dport $PORT -j ACCEPT
	systemctl daemon-reload
	systemctl restart docker
fi