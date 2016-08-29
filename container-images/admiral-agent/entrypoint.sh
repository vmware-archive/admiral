#!/bin/sh

touch /var/log/containerproxymapper.log
touch /var/log/proxyreconfigure.log
touch /var/log/shellserver.log
touch /var/log/haproxy.log

if [ -f /var/run/rsyslogd.pid ];
then
    /bin/kill `cat /var/run/rsyslogd.pid 2> /dev/null` 2> /dev/null || true
fi

rsyslogd

if [ -f /haproxy/haproxy.cfg ];
then
    # haproxy config already exists, start haproxy (in case of container restart)
   /haproxy/haproxy-reload.sh
fi

nohup /agent/shellserver >/dev/null 2>&1 &

cat admiral_logo.txt
echo "Admiral Agent started successfully!"

/agent/containerproxymapper