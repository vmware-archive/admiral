PORT=$1

RELEASE=$(cat /etc/*-release)
echo $RELEASE | grep CoreOS
if [ $? -eq 0 ]
then
	echo "Installing on CoreOS"
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

	sed -i "s/{{PORT}}/$1/" docker.service
	cp -f docker.service /lib/systemd/system/
	systemctl daemon-reload
	service docker restart
fi

echo $RELEASE | grep PhotonOS
if [ $? -eq 0 ]
then
	echo "Installing on PhotonOS"
fi