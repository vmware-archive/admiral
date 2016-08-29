package main

import (
    "log"
    "github.com/fsouza/go-dockerclient"
    "os/exec"
    "fmt"
    "os"
    "cmp/common"
)

const MAP_FILE = "/haproxy/containers.map"

func main() {
    var logErr = common.StartLoggingToFile("/var/log/containerproxymapper.log", "containerproxymapper")
    if logErr != nil {
        log.Fatal(logErr)
        os.Exit(1)
    }

    startDockerEventListener()
}

func startDockerEventListener() {
    endpoint := "unix:///var/run/docker.sock"
    client, _ := docker.NewClient(endpoint)
    listener := make(chan *docker.APIEvents, 10)
    client.AddEventListener(listener)

    for {
        select {
            case msg := <-listener:
                log.Printf("Received: %v", *msg)
                handleEvent(client, msg)
        }
    }
 }

func handleEvent(client *docker.Client, msg *docker.APIEvents) {
    switch msg.Status {
        case "start":
            var container, err = client.InspectContainer(msg.ID)
            if err != nil {
                log.Fatal(err)
                return
            }
            // remove the prepending "/"
            var nameOnly = container.Name[1:]
            var updateErr = updateMap(nameOnly, container.NetworkSettings.IPAddress)
            if updateErr != nil {
                log.Fatal(updateErr)
                return
            }
            common.ReloadHAproxy()
    }
}

func updateMap(containeName string, containerIp string) error {
    // Cleanup old entry for the same name or ip if exists
    errClean := exec.Command("sed",
        "-i",
        "-re",
        fmt.Sprintf("/^(%s )|( %s)$/d", containerIp, containeName),
        MAP_FILE).Run()

    if errClean != nil {
        return fmt.Errorf("Error cleaning up old entry from file %v", errClean)
    }

    // Append the new entry
    f, errOpen := os.OpenFile(MAP_FILE, os.O_APPEND|os.O_WRONLY, 0600)
    if errOpen != nil {
        return fmt.Errorf("Error opening file %v", errOpen)
    }

    defer f.Close()

    _, errWrite := f.WriteString(fmt.Sprintf("%s %s\n", containerIp, containeName))
    if errWrite != nil {
        return fmt.Errorf("Error updating file %v", errWrite)
    }
    log.Printf("Updated map with entry: %s %s", containerIp, containeName)

    return nil
}