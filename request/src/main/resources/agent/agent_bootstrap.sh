#!/bin/bash
# Copyright (c) 2012-2016 VMware, Inc.  All rights reserved.
#
# Description: VMware vCloud vRA Software agent download and
# execution script.
#

WORKDIR=/opt/vmware/agent
SCRIPTDIR=/opt/vmware/agent-bootstrap
AGENT_RAN_FLAG=$WORKDIR/agent_ran
AGENT_FAILED_FLAG=$WORKDIR/bootstrap_failed
AGENT_PROP_FILE=appd.properties
APPD_PROPERTIES=

WGET_OPTIONS="--no-check-certificate --timeout=60 --tries=5 --wait=10 --random-wait --retry-connrefused --debug --append-output=$AGENT_DOWNLOAD_LOG"
CURL_OPTIONS="-k --connect-timeout 60 --retry 5 --retry-delay 10 -# --stderr - -v"
SLEEP_INTERVAL=30
# SIX HOURS of Seconds
WAIT_LIMIT=21600

#
# echo msg with timestamp
#
echo_d()
{
    CURDATE=`date`
    echo -e $CURDATE  "$*"
}

#
# Display stderr
#
stderr()
{
    CURDATE=`date`
    echo -e $CURDATE  "$*" 1>&2
    echo $CURDATE >> $AGENT_FAILED_FLAG
    echo "$*" >> $AGENT_FAILED_FLAG
}

#
# Verify the existence of a command
#
cmdExist()
{
    input="$*"

    if type "$input" > /dev/null 2>&1
    then
        :;
    else
        stderr "$input command missing."
        return 1
    fi
}

#
# Verify the existence of a command
#
cmdExistNoError()
{
    input="$*"

    if type "$input" > /dev/null 2>&1
    then
        :;
    else
        return 1
    fi
}

retrieveAgentPropFile()
{
    APPD_PROPERTIES=$WORKDIR/$AGENT_PROP_FILE
    echo_d "Found and will use $APPD_PROPERTIES."
    chmod 400 $APPD_PROPERTIES
}

#
# Retrieve agent.download.url from appd.properties
#
function retrieveAgentUrl()
{
    if [ ! x"$APPD_PROPERTIES" = x ]; then
        url=`grep "agent.download.url=" $APPD_PROPERTIES | sed 's,agent.download.url=,,g' 2>&1`
        echo $url
    else
        echo
    fi
}

#
# Retrieve agent.jar.sha256sum from appd.properties
#
function retrieveSHA256AgentSum()
{
    if [ ! x"$APPD_PROPERTIES" = x ]; then
        sum=`grep "agent.jar.sha256sum=" $APPD_PROPERTIES | sed 's,agent.jar.sha256sum=,,g' 2>&1`
        echo $sum
    else
        echo
    fi
}

#
# Download vRA Software agent jar file from vCAC Software server
#
retrieveAgent()
{
    cd $WORKDIR

    agent_sum=$(retrieveSHA256AgentSum)
    if [ -z $agent_sum ]; then
        stderr "Empty checksum for agent file."
        return 1
    fi

    cmdExist "sha256sum"
    ret=$?
    if [ ! $ret = 0 ]; then
        return 1
    fi

    cmdExist "awk"
    ret=$?
    if [ ! $ret = 0 ]; then
        return 1
    fi

    agent_url=$(retrieveAgentUrl)
    if [[ ! $agent_url =~ http[s]?://.* ]]; then
        stderr "Invalid agent download URL."
        return 1
    fi

    agent_filename="${agent_url##*/}"

    if [ -f $agent_filename ]; then
        echo_d "Found existing $agent_filename in the VM."
        localsum=`sha256sum $agent_filename | awk '{print $1}'`
        if [ x"$localsum" = x"$agent_sum" ]; then
            echo_d "sha256sum matched. Use existing $agent_filename."
            return 0
        else
            echo_d "sha256sum does not match. Removing $agent_filename ..."
            rm $agent_filename
        fi

    fi

    echo_d "Downloading $agent_url..."

    cmdExistNoError "wget"
    ret=$?
    if [ $ret = 0 ]; then
        wget $WGET_OPTIONS -O $agent_filename $agent_url
        ret=$?
        if [ ! $ret = 0 ]; then
            stderr "WGET operation failed. Check agent_download.log. Abort."
            return 1
        fi
    else
        cmdExistNoError "curl"
        ret=$?
        if [ $ret = 0 ]; then
            curl $CURL_OPTIONS -o $agent_filename $agent_url > $AGENT_DOWNLOAD_LOG
            ret=$?
            if [ ! $ret = 0 ]; then
                stderr "CURL operation failed. Check agent_download.log. Abort."
                return 1
            fi
        else
            stderr "wget and curl commands missing. Abort."
            return 1
        fi
    fi

    if [ ! -f $agent_filename ]; then
        stderr "$agent_filename not found after downloading."
        return 1
    fi

    localsum=`sha256sum $agent_filename | awk '{print $1}'`
    if [ ! x"$localsum" = x"$agent_sum" ]; then
        stderr "Downloaded $agent_filename does not have the matching checksum registered in $APPD_PROPERTIES."
        rm $agent_filename
        return 1
    fi

    return 0
}

#
# Launch vRA Software agent to execute deployment tasks.
#
runExecutableAgent()
{
    agent_filename="$*"

    echo_d "Executing $agent_filename..."
    chmod +x $agent_filename
    exec ./$agent_filename

    return 0
}

#
# Launch non-Java vRA Software agent to execute deployment tasks.
#
runZipAgent()
{
    agent_filename="$*"

    cd $WORKDIR
    contained_agent_filename="${agent_filename%.*}".`uname -s`_`uname -m`

    if [ ! -f $contained_agent_filename ]; then
        unzip $agent_filename
    fi

    if [ -f $contained_agent_filename ]; then
        runExecutableAgent $contained_agent_filename
        return 0
    else
        stderr "$contained_agent_filename missing."
        return 1
    fi
}

if [ -f $AGENT_FAILED_FLAG ]; then
    # This will cause a recreation of $AGENT_FAILED_FLAG to properly indicate an error in the Guest Agent
    mv $AGENT_FAILED_FLAG $AGENT_FAILED_FLAG.old
    stderr "The template for this deployment has a failure from a previous use. To reset agent bootstrap to a clean status, instantiate a VM from the template, run $SCRIPTDIR/agent_reset.sh, shutdown the VM, and use that as a new template for deployment."
    exit 1
fi

if [ ! -d $WORKDIR ]; then
    mkdir -p $WORKDIR
fi

retrieveAgentPropFile
if [ -f $AGENT_RAN_FLAG ]; then
   echo_d "vRA Software agent previously executed; this is a re-execution of the vRA Software Agent, perhaps after a reboot."
fi

retrieveAgent
ret=$?
if [ ! $ret = 0 ]; then
    stderr "Failed to retrieve vRA Software agent."
    stderr "==Running diagnostic script=="
    $SCRIPTDIR/agent_diagnose.sh
    exit 1
fi

agent_url=$(retrieveAgentUrl)
agent_filename="${agent_url##*/}"
if [ ${agent_filename##*.} == zip ] ; then
    runZipAgent $agent_filename
    ret=$?
    if [ ! $ret = 0 ]; then
        exit 1
    fi
else
    runExecutableAgent $agent_filename
    ret=$?
    if [ ! $ret = 0 ]; then
        exit 1
    fi
fi

date > $AGENT_RAN_FLAG

exit 0
