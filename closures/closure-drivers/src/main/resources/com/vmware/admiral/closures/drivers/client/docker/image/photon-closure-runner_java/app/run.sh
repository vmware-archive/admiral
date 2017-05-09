#!/bin/bash

PATH=$PATH:/var/opt/OpenJDK-1.8.0.112-bin/bin
export PATH
mkdir ./user_scripts || echo "File exits"
echo "${TRUST_CERTS}" > trust.pem
javac -cp \* com/vmware/admiral/closure/AppRunner.java com/vmware/admiral/closure/Context.java
java -cp .:\* com/vmware/admiral/closure/AppRunner