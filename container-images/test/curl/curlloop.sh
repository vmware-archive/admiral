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

max_count=-1
if [ ! -z $1 ]
then
    max_count=$1
fi

sleepinterval=0.2
if [ ! -z $2 ]
then
    sleepinterval=$2
fi

count=1
while true
do
    printf "\n%d - " "$count"
    curl identity:80
    count=$((count+1))

    if [ "$max_count" -gt "-1" ] && [ "$count" -gt "$max_count" ]
    then
        break
    fi
    sleep $sleepinterval
done

echo -e "\nDone with curl loop. Continue in sh"
sh