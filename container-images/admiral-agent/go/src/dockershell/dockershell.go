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
