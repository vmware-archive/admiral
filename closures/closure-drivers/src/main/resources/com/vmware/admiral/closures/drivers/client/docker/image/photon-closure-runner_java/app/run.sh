#!/bin/bash

PATH=$PATH:/var/opt/OpenJDK-1.8.0.112-bin/bin
export PATH
mkdir ./user_scripts || echo "File exits"
echo "${TRUST_CERTS}" > trust.pem
javac -cp \* AppRunner.java
java -cp .:\* AppRunner