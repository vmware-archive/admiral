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