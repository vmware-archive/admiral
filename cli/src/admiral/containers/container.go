package containers

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"regexp"
	"strings"
	"time"

	"admiral/functions"
)

type Container struct {
	Id               string      `json:"id"`
	Address          string      `json:"address"`
	Names            []string    `json:"names"`
	PowerState       string      `json:"powerState"`
	Ports            []Port      `json:"ports"`
	DescriptionLink  string      `json:"descriptionLink"`
	System           bool        `json:"system"`
	Created          int64       `json:"created"`
	Started          int64       `json:"started"`
	Command          []string    `json:"command"`
	PolicyLink       string      `json:"groupResourcePolicyLink"`
	Attributes       Attrirbutes `json:"attributes"`
	DocumentSelfLink string      `json:"documentSelfLink"`
}

func (c *Container) GetID() string {
	return strings.Replace(c.DocumentSelfLink, "/resources/containers/", "", -1)
}

//GetPorts returns exposed ports of the container as string.
//The format is similar to [XXXX:XXXX YYYY:YYYY]
//This is commonly used for printing
func (c *Container) GetPorts() string {
	var output bytes.Buffer
	output.WriteString("[")
	for i := range c.Ports {
		output.WriteString(c.Ports[i].String())
		if i != len(c.Ports)-1 {
			output.WriteString(" ")
		}
	}
	output.WriteString("]")
	return output.String()
}

//GetCreated returns string that contains converted timestamp
//to the format "%d hours/minutes/seconds ago".
//This is commonly used for printing.
func (c *Container) GetCreated() string {
	then := time.Unix(0, c.Created*int64(time.Millisecond))
	timeSinceCreate := time.Now().Sub(then)
	if timeSinceCreate.Hours() > 1 {
		return fmt.Sprintf("%d hours ago", int64(timeSinceCreate.Hours()))
	}
	if timeSinceCreate.Minutes() > 1 {
		return fmt.Sprintf("%d minutes ago", int64(timeSinceCreate.Minutes()))
	}
	if timeSinceCreate.Seconds() > 1 {
		return fmt.Sprintf("%d seconds ago", int64(timeSinceCreate.Seconds()))
	}
	return "0 seconds ago"
}

//GetCreated returns string that contains converted timestamp
//to the format "%d hours/minutes/seconds ago".
//This is commonly used for printing.
func (c *Container) GetStarted() string {
	then := time.Unix(0, c.Started*int64(time.Millisecond))
	timeSinceStart := time.Now().Sub(then)
	if timeSinceStart.Hours() > 1 {
		return fmt.Sprintf("%d hours ago", int64(timeSinceStart.Hours()))
	}
	if timeSinceStart.Minutes() > 1 {
		return fmt.Sprintf("%d minutes ago", int64(timeSinceStart.Minutes()))
	}
	if timeSinceStart.Seconds() > 1 {
		return fmt.Sprintf("%d seconds ago", int64(timeSinceStart.Seconds()))
	}
	return "0 seconds ago"
}

//StringJson returns the Container to string in json format.
func (c *Container) StringJson() string {
	jsonBody, err := json.MarshalIndent(c, "", "    ")
	functions.CheckJson(err)
	return string(jsonBody)
}

type Port struct {
	HostPort      string `json:"hostPort"`
	ContainerPort string `json:"containerPort"`
}

//String returns ports to string in format "HostPort:ContainerPort"
func (p Port) String() string {
	return fmt.Sprintf("%s:%s", p.HostPort, p.ContainerPort)
}

//SetPorts is setting the host and container port fields.
//Expected format of the parameter is "HostPort:ContainerPort"
func (p *Port) SetPorts(s string) {
	r, _ := regexp.Compile("[0-9]+\\:[0-9]+")
	if !r.MatchString(s) {
		fmt.Println("Invalid format of ports. \n Usage: \n    -p hostPort:containerPort \n Example:    -p 9080:80")
		os.Exit(0)
	}
	pArr := strings.Split(r.FindString(s), ":")
	p.HostPort = pArr[0]
	p.ContainerPort = pArr[1]
}

type NetworkSettings string

//MarshalJSON is required function in order to implement the interface
//used from the json marshaller for custom marshalling.
func (ns *NetworkSettings) MarshalJSON() ([]byte, error) {
	output := strings.Replace(string(*ns), "\\", "", -1)
	var v interface{}
	json.Unmarshal([]byte(output), &v)
	return json.Marshal(v)
}

type Attrirbutes struct {
	Driver          string          `json:"Driver"`
	ImageHash       string          `json:"Image"`
	NetworkSettings NetworkSettings `json:"NetworkSettings"`
}
