#
# Copyright (c) 2016 VMware, Inc. All Rights Reserved.
#
# This product is licensed to you under the Apache License, Version 2.0 (the "License").
# You may not use this product except in compliance with the License.
#
# This product may include a number of subcomponents with separate copyright notices
# and license terms. Your use of these subcomponents is subject to the terms and
# conditions of the subcomponent's license, as noted in the LICENSE file.
#

#!/bin/sh

# A small tool that boots up admiral on a host and start managing it.

echo "Setting up Admiral to manage your host..."
sleep 1

# Looking for known locations containing server certificates
## Docker machine
if [ -d ~/.docker/machine ];then
    read -p  "Using docker-machine? (y/n)? [y] " answer
    answer=${answer:-y}

    if echo "$answer" | grep -iq "^y" ;then
        read -p "Enter docker-machine name [default]: " machine
        machine=${machine:-default}

        cert="$HOME/.docker/machine/machines/$machine/cert.pem"
        key="$HOME/.docker/machine/machines/$machine/key.pem"
    fi
fi

## Docker machine (native)
if [[ ! -f "$cert" || ! -f "$key" ]];then
    cert="/var/lib/boot2docker/server.pem"
    key="/var/lib/boot2docker/server-key.pem"
fi

## CoreOS
if [[ ! -f "$cert" || ! -f "$key" ]];then
    cert="/etc/docker/server.pem"
    key="/etc/docker/server-key.pem"
fi

if [[ -f "$cert" && -f "$key" ]];then
    read -p  "Found certificate ($cert) and key ($key), do you want to use them to authenticate to the host? (Y/n) " answer
    answer=${answer:-y}

    if ! echo "$answer" | grep -iq "^y" ;then
        echo "Not using default files."
        cert=""
        key=""
    fi
fi

if [[ ! -f "$cert" || ! -f "$key" ]];then
    read -p  "Does your host require certificate authentication? (Y/n) " answer
    answer=${answer:-y}

    if echo "$answer" | grep -iq "^y" ;then
        read -p  "Provide Docker host certificate location: " cert
        read -p  "Provide Docker host certificate key location: " key

        if [ ! -f "$cert" ];then
            echo "Could not find host certificate at $cert"
            exit 1
        fi

        if [ ! -f "$key" ];then
            echo "Could not find host certificate key at $key"
            exit 1
        fi
    fi
fi

dockerip=$(/sbin/ip route|awk '/docker0/ { print $5 }')
connectionstring="-H=$dockerip:2375"
if [[ -f "$cert" && -f "$key" ]];then
    connectionstring="-H=$dockerip:2376 --tls --tlscert=$cert --tlskey=$key"
fi

echo "Starting Admiral server..."

# Run Admiral container
containerid=$(docker $connectionstring run -d -P -e JAVA_OPTS="-Xmx1024M -Xms1024M -Xss256K -Xmn356M" vmware/admiral)

sleep 1

containerip=$(docker $connectionstring inspect --format '{{ .NetworkSettings.IPAddress }}' "$containerid")
address="http://$containerip:8282"

count=1
while true
do
    # Check for credentials service to be up and running
    HTTP_RESPONSE=$(curl --silent --write-out "HTTPSTATUS:%{http_code}" -X GET "$address/core/auth/credentials")
    HTTP_STATUS=$(echo $HTTP_RESPONSE | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')

    if [ $HTTP_STATUS -eq 200 ]; then
        # Check for docker adapter service to be up and running
        HTTP_RESPONSE=$(curl --silent --write-out "HTTPSTATUS:%{http_code}" -X GET "$address/adapters/host-docker-service")
        HTTP_STATUS=$(echo $HTTP_RESPONSE | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    fi

    if [ $HTTP_STATUS -eq 200 ]; then
        sleep 2
        echo "    Admiral server started!"
        break
    fi
    if [ "$count" -gt 20 ]
    then
        echo "Admiral server could not start!"
        exit 1
    fi

    count=$((count+1))
    sleep 1
done


dockeraddress="http://$dockerip:2375"
accepthostvalue="true"
credentailsproperty=""
if [[ -f "$cert" && -f "$key" ]];then
    dockeraddress="https://$dockerip:2376"
    accepthostvalue="false"

    certcontent=$(cat $cert)
    keycontent=$(cat $key)

    credential="{\"type\": \"PublicKey\", \"documentSelfLink\": \"cred-$dockerip\", \"privateKey\": \"$keycontent\", \"publicKey\": \"$certcontent\"}"

    echo "Creating credential for host..."

    HTTP_RESPONSE=$(curl --silent --write-out "HTTPSTATUS:%{http_code}" -H "Content-Type: application/json" -X POST -d "${credential}" "$address/core/auth/credentials")
    HTTP_BODY=$(echo $HTTP_RESPONSE | sed -e 's/HTTPSTATUS\:.*//g')
    HTTP_STATUS=$(echo $HTTP_RESPONSE | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')

    if [ ! $HTTP_STATUS -eq 200  ]; then
        echo "Error creating credential [HTTP status: $HTTP_STATUS]"
        echo "$HTTP_BODY"
        exit 1
    fi

    credentailsproperty="\"__authCredentialsLink\": \"/core/auth/credentials/cred-$dockerip\", "
    echo "    Credential created."
fi

host="{\"hostState\": {\"address\": \"$dockeraddress\", \"id\": \"$dockerip\", \"resourcePoolLink\": \"/resources/pools/default-resource-pool\", \"customProperties\": {$credentailsproperty\"__adapterDockerType\": \"API\"}}, \"acceptCertificate\": true, \"acceptHostAddress\": $accepthostvalue}"

echo "Creating host..."

HTTP_RESPONSE=$(curl --silent --write-out "HTTPSTATUS:%{http_code}" -H "Content-Type: application/json" -X PUT -d "$host" "$address/resources/hosts")
HTTP_BODY=$(echo $HTTP_RESPONSE | sed -e 's/HTTPSTATUS\:.*//g')
HTTP_STATUS=$(echo $HTTP_RESPONSE | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')

if [ ! $HTTP_STATUS -eq 204  ]; then
    echo "Error creating host [HTTP status: $HTTP_STATUS]"
    echo "$HTTP_BODY"
    exit 1
fi

echo "    Host created."

echo "Everything done!"

globalips=$(ip -4 -o addr show scope global | grep -e 'eth[0-9]' -e 'eno[0-9]' | awk '{gsub(/\/.*/,"",$4); print $4}')

if [ "$globalips" == "" ];then
    echo "Could not locate global ips on the host to connect to."
    exit 0
fi

externalport=$(docker $connectionstring inspect --format '{{ (index (index .NetworkSettings.Ports "8282/tcp") 0).HostPort }}' "$containerid")

cat /admiral_logo.txt

echo "Admiral is running on:"

printf '%s\n' "$globalips" | while read -r ip
do
    echo "    http://${ip}:${externalport}/uic/"
done

