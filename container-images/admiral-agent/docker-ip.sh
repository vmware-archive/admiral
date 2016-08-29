#!/bin/sh

ip route|awk '/docker0/ { print $9 }'