/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package main

import (
	"github.com/fgrehm/go-dockerpty"
	"github.com/fsouza/go-dockerclient"
	"log"
	"os"
)

const MAP_FILE = "/haproxy/containers.map"

func main() {
	endpoint := "unix:///var/run/docker.sock"
	client, err := docker.NewClient(endpoint)

	if err != nil {
		log.Fatal(err)
		os.Exit(1)
	}

	if len(os.Args) != 2 || os.Args[1] == "" {
		log.Fatal("No container id passed as agument. Usage: dockershell CONTAINER")
		os.Exit(1)
	}

	var containerId = os.Args[1]

	// prefer bash but only sh could be available
	var cmd = []string{"sh", "-c", "[ -x /bin/bash ] && exec /bin/bash || exec /bin/sh"}

	createOptions := docker.CreateExecOptions{
		Container:    containerId,
		AttachStdin:  true,
		AttachStdout: true,
		AttachStderr: true,
		Tty:          true,
		Cmd:          cmd,
		User:         "root",
	}

	execObj, createErr := client.CreateExec(createOptions)
	if createErr != nil {
		log.Fatal(createErr)
		os.Exit(1)
	}

	startErr := dockerpty.StartExec(client, execObj)
	if startErr != nil {
		log.Fatal(startErr)
		os.Exit(1)
	}
}
