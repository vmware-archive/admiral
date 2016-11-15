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
	"errors"
	"fmt"
	"net/http"
	"os"
	"regexp"
	"strings"
	"time"

	"admiral/client"
	"admiral/config"
	"admiral/track"
	"admiral/utils"
	"admiral/utils/selflink"
)

var (
	ContainersNotProvidedError   = errors.New("No containers provided.")
	ContainersNotMatchQueryError = errors.New("No containers found to match the query.")
)

type Container struct {
	Id               string                 `json:"id"`
	Address          string                 `json:"address"`
	Names            []string               `json:"names"`
	PowerState       string                 `json:"powerState"`
	Ports            []Port                 `json:"ports"`
	Networks         map[string]interface{} `json:"networks"`
	DescriptionLink  string                 `json:"descriptionLink"`
	System           bool                   `json:"system"`
	Created          int64                  `json:"created"`
	Started          int64                  `json:"started"`
	Command          []string               `json:"command"`
	PolicyLink       string                 `json:"groupResourcePolicyLink"`
	Attributes       Attributes             `json:"attributes"`
	DocumentSelfLink string                 `json:"documentSelfLink"`
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
	utils.CheckJsonError(err)
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

type Attributes struct {
	Driver          string          `json:"Driver"`
	ImageHash       string          `json:"Image"`
	NetworkSettings NetworkSettings `json:"NetworkSettings"`
}

type CommandHolder struct {
	Command []string `json:"command"`
}

type OperationContainer struct {
	Operation     string   `json:"operation"`
	ResourceLinks []string `json:"resourceLinks"`
	ResourceType  string   `json:"resourceType"`
}

var (
	resLinks []string
)

//Function to start container by it's name.
//Returns boolean result if it starting or not.
func StartContainer(containers []string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	fullIds, err := selflink.GetFullIds(containers, new(ListContainers), utils.CONTAINER)
	utils.CheckIdError(err)
	links := utils.CreateResLinksForContainer(fullIds)

	if len(containers) < 1 || containers[0] == "" {
		return nil, ContainersNotProvidedError
	}
	newStart := OperationContainer{
		Operation:     "Container.Start",
		ResourceLinks: links,
		ResourceType:  "DOCKER_CONTAINER",
	}

	jsonBody, err := json.Marshal(newStart)
	utils.CheckJsonError(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}

	taskStatus := &track.OperationResponse{}
	_ = json.Unmarshal(respBody, taskStatus)
	taskStatus.PrintTracerId()
	if !asyncTask {
		resLinks, err = track.Wait(taskStatus.GetTracerId())
	} else {
		resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
		if len(resLinks) < 1 {
			return containers, err
		}
	}
	resourcesIDs := utils.GetResourceIDs(resLinks)
	return resourcesIDs, err

}

//Function to stop container by it's name.
//Returns boolean result if it stopping or not.
func StopContainer(containers []string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	fullIds, err := selflink.GetFullIds(containers, new(ListContainers), utils.CONTAINER)
	utils.CheckIdError(err)
	links := utils.CreateResLinksForContainer(fullIds)

	newStop := OperationContainer{
		Operation:     "Container.Stop",
		ResourceLinks: links,
		ResourceType:  "DOCKER_CONTAINER",
	}

	jsonBody, err := json.Marshal(newStop)

	utils.CheckJsonError(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	taskStatus := &track.OperationResponse{}
	_ = json.Unmarshal(respBody, taskStatus)
	taskStatus.PrintTracerId()
	if !asyncTask {
		resLinks, err = track.Wait(taskStatus.GetTracerId())
	} else {
		resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
		if len(resLinks) < 1 {
			return containers, err
		}
	}
	resourcesIDs := utils.GetResourceIDs(resLinks)
	return resourcesIDs, err

}

//Function to remove container by it's name.
//Returns boolean result if it removing or not.
func RemoveContainer(containers []string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	fullIds, err := selflink.GetFullIds(containers, new(ListContainers), utils.CONTAINER)
	utils.CheckIdError(err)
	links := utils.CreateResLinksForContainer(fullIds)

	newRemoveContainer := OperationContainer{
		Operation:     "Container.Delete",
		ResourceLinks: links,
		ResourceType:  "DOCKER_CONTAINER",
	}

	jsonBody, err := json.Marshal(newRemoveContainer)

	utils.CheckJsonError(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return nil, respErr
	}
	taskStatus := &track.OperationResponse{}
	_ = json.Unmarshal(respBody, taskStatus)
	taskStatus.PrintTracerId()
	if !asyncTask {
		resLinks, err = track.Wait(taskStatus.GetTracerId())
	} else {
		resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
		if len(resLinks) < 1 {
			return containers, err
		}
	}
	resourcesIDs := utils.GetResourceIDs(resLinks)
	return resourcesIDs, err

}

//Function to remove many containers matching specified query
//Returns boolean result if they are removing or not.
func RemoveMany(container string, asyncTask bool) ([]string, error) {
	lc := &ListContainers{}
	count, err := lc.FetchContainers(container)
	if err != nil {
		return nil, err
	}
	if count < 1 {
		return nil, ContainersNotMatchQueryError
	}
	url := config.URL + "/requests"
	newRemoveContainer := OperationContainer{
		Operation:     "Container.Delete",
		ResourceLinks: lc.DocumentLinks,
		ResourceType:  "DOCKER_CONTAINER",
	}

	jsonBody, err := json.Marshal(newRemoveContainer)

	utils.CheckJsonError(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)

	if respErr == nil {
		taskStatus := &track.OperationResponse{}
		_ = json.Unmarshal(respBody, taskStatus)
		taskStatus.PrintTracerId()
		if !asyncTask {
			resLinks, err = track.Wait(taskStatus.GetTracerId())
		} else {
			resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
			if len(resLinks) < 1 {
				return utils.GetResourceIDs(lc.DocumentLinks), err
			}
		}
		resourcesIDs := utils.GetResourceIDs(resLinks)
		return resourcesIDs, err
	}
	return nil, respErr
}

//Function to execute command inside container.
func ExecuteCmd(container string, execF string) {
	fullIds, err := selflink.GetFullIds([]string{container}, new(ListContainers), utils.CONTAINER)
	utils.CheckIdError(err)
	links := utils.CreateResLinksForContainer(fullIds)
	contLink := links[0]
	exec := strings.Split(execF, " ")
	url := config.URL + "/exec?containerLink=" + contLink
	ch := CommandHolder{
		Command: exec,
	}
	jsonBody, err := json.Marshal(ch)
	utils.CheckJsonError(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		fmt.Println(respErr)
	} else {
		fmt.Print(string(respBody))
	}
}

//Function to scale container by it's name with some count provided as parameter.
func ScaleContainer(containerID string, scaleCount int32, asyncTask bool) (string, error) {
	fullIds, err := selflink.GetFullIds([]string{containerID}, new(ListContainers), utils.CONTAINER)
	utils.CheckIdError(err)
	links := utils.CreateResLinksForContainer(fullIds)
	url := config.URL + links[0]
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	container := &Container{}
	err = json.Unmarshal(respBody, container)
	utils.CheckJsonError(err)
	contDesc := container.DescriptionLink

	url = config.URL + "/requests"
	scale := OperationScale{
		Operation:               "CLUSTER_RESOURCE",
		ResourceDescriptionLink: contDesc,
		ResourceType:            "DOCKER_CONTAINER",
		ResourceCount:           scaleCount,
	}

	scaleJson, err := json.Marshal(scale)
	utils.CheckJsonError(err)

	req, _ = http.NewRequest("POST", url, bytes.NewBuffer(scaleJson))
	_, respBody, respErr = client.ProcessRequest(req)

	if respErr != nil {
		return "", respErr
	}
	taskStatus := &track.OperationResponse{}
	_ = json.Unmarshal(respBody, taskStatus)
	taskStatus.PrintTracerId()
	if !asyncTask {
		resLinks, err = track.Wait(taskStatus.GetTracerId())
	} else {
		resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
	}
	return containerID, err

}

//Function to get information about container in JSON format.
func InspectContainer(id string) ([]byte, error) {
	fullIds, err := selflink.GetFullIds([]string{id}, new(ListContainers), utils.CONTAINER)
	utils.CheckIdError(err)
	links := utils.CreateResLinksForContainer(fullIds)
	url := config.URL + links[0]
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	respBodyString := string(respBody)
	respBodyString = strings.Replace(respBodyString, "\\\"", "\"", -1)
	respBodyString = strings.Replace(respBodyString, "\"{", "{", -1)
	respBodyString = strings.Replace(respBodyString, "}\"", "}", -1)
	respBodyString = strings.Replace(respBodyString, "\"[", "[", -1)
	respBodyString = strings.Replace(respBodyString, "]\"", "]", -1)
	var buffer bytes.Buffer
	json.Indent(&buffer, []byte(respBodyString), "", "    ")
	return buffer.Bytes(), nil
}

func GetContainer(id string) *Container {
	fullId, err := selflink.GetFullId(id, new(ListContainers), utils.CONTAINER)
	utils.CheckIdError(err)
	link := utils.CreateResLinksForContainer([]string{fullId})[0]
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	utils.CheckResponse(respErr, url)
	c := &Container{}
	err = json.Unmarshal(respBody, c)
	utils.CheckJsonError(err)
	return c
}

func GetContainerDescription(id string) *ContainerDescription {
	link := utils.CreateResLinkForContainerDescription(id)
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	utils.CheckResponse(respErr, url)
	cd := &ContainerDescription{}
	err := json.Unmarshal(respBody, cd)
	utils.CheckJsonError(err)
	return cd
}

type RunContainer struct {
	ResourceDescriptionLink string   `json:"resourceDescriptionLink"`
	ResourceType            string   `json:"resourceType"`
	TenantLinks             []string `json:"tenantLinks"`
}

type OperationScale struct {
	Operation               string `json:"operation"`
	ResourceDescriptionLink string `json:"resourceDescriptionLink"`
	ResourceType            string `json:"resourceType"`
	ResourceCount           int32  `json:"resourceCount"`
}
