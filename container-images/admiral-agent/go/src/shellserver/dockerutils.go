package main

import (
    "github.com/fsouza/go-dockerclient"
)

var endpoint = "unix:///var/run/docker.sock"
var client, _ = docker.NewClient(endpoint)

func IsContainerRunning(containerNameOrId string) (bool, error) {
    var container, err = client.InspectContainer(containerNameOrId)
    if err != nil {
        return false, err
    }

    return container.State.Running, nil
}

func GetContainerId(containerNameOrId string) (string, error) {
    var container, err = client.InspectContainer(containerNameOrId)
    if err != nil {
        return "", err
    }
    return container.ID, nil
}