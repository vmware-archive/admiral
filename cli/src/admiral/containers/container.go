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

//GetID returns the ID of the container.
func (c *Container) GetID() string {
	return strings.Replace(c.DocumentSelfLink, "/resources/containers/", "", -1)
}

//GetExternalID returns shorten external ID similar
//to how docker ps shows containers IDs.
func (c *Container) GetExternalID() string {
	if len(c.Id) <= 14 {
		return c.Id
	}
	return c.Id[0:15]
}

//GetStatus returns the power state of the container.
//If the status is "RUNNING" it also contains since when it is running.
func (c *Container) GetStatus() string {
	if c.PowerState != "RUNNING" {
		return c.PowerState
	}
	status := fmt.Sprintf("%s %s", c.PowerState, c.GetStarted())
	return status
}

//GetPorts returns exposed ports of the container as string.
//The format is similar to [XXXX:XXXX YYYY:YYYY]
//This is commonly used for printing
func (c *Container) GetPorts() string {
	var output bytes.Buffer
	output.WriteString("[")
	if len(c.Ports) > 0 && len(c.Ports) < 2 {
		output.WriteString(c.Ports[0].String())
	} else if len(c.Ports) >= 2 {
		output.WriteString(c.Ports[0].String())
		output.WriteString("...")
	}
	output.WriteString("]")
	return output.String()
}

//GetCreated returns string that contains converted timestamp
//to the format "%d hours/minutes/seconds ago".
//This is commonly used for printing.
func (c *Container) GetCreated() string {
	if c.Created <= 0 {
		return "unknown"
	}
	then := time.Unix(0, c.Created*int64(time.Millisecond))
	timeSinceCreate := time.Now().Sub(then)
	if timeSinceCreate.Hours() > 72 {
		daysAgo := int(float64(timeSinceCreate.Hours()) / 24.0)
		return fmt.Sprintf("%d days ago", daysAgo)
	}
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
	if c.Started <= 0 {
		return "unknown"
	}
	then := time.Unix(0, c.Started*int64(time.Millisecond))
	timeSinceStart := time.Now().Sub(then)
	if timeSinceStart.Hours() > 72 {
		daysAgo := int(float64(timeSinceStart.Hours()) / 24.0)
		return fmt.Sprintf("%d days", daysAgo)
	}
	if timeSinceStart.Hours() > 1 {
		return fmt.Sprintf("%d hours", int64(timeSinceStart.Hours()))
	}
	if timeSinceStart.Minutes() > 1 {
		return fmt.Sprintf("%d minutes", int64(timeSinceStart.Minutes()))
	}
	if timeSinceStart.Seconds() > 1 {
		return fmt.Sprintf("%d seconds", int64(timeSinceStart.Seconds()))
	}
	return "0 seconds"
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
	r, _ := regexp.Compile("[0-9]+:[0-9]+")
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
