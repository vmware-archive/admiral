#!/bin/sh
# Script that synchronizes haproxy reloads. Regardless of the number of concurrent
# processes that will reload the haproxy, this will guarantee that it is
# executed one at a time.

(
    # Wait for lock on /haproxy/haproxy-reload.lock (fd 200)
    flock -x 200

    # Do the actual reloading
    if [ -f /haproxy/haproxy.pid ]
    then
        haproxy -D -f /haproxy/haproxy.cfg -p /haproxy/haproxy.pid -sf $(cat /haproxy/haproxy.pid)
    else
        haproxy -D -f /haproxy/haproxy.cfg -p /haproxy/haproxy.pid
    fi

    # Release lock
    flock -u 200
) 200>/haproxy/haproxy-reload.lock