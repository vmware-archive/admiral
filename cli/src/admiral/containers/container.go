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
	"admiral/utils/urlutils"
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
	utils.CheckBlockingError(err)
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
		fmt.Fprintln(os.Stderr, "Invalid format of ports. \n Usage: \n    -p hostPort:containerPort \n Example:    -p 9080:80")
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

// ContainersOperator is required interface to achieve polymorphism
// for ContainersOperation and ContainersOperationScale structures.
// It is used in the function processContainersOperation().
type ContainersOperator interface {
	GetOperation() string
}

type ContainersOperation struct {
	Operation     string   `json:"operation"`
	ResourceLinks []string `json:"resourceLinks,omitempty"`
	ResourceType  string   `json:"resourceType"`
}

func (co *ContainersOperation) GetOperation() string {
	return co.Operation
}

type ContainersOperationScale struct {
	ContainersOperation
	ResourceDescriptionLink string `json:"resourceDescriptionLink"`
	ResourceCount           int32  `json:"resourceCount"`
}

// StartContainer starts containers by their IDs.
// As second parameter takes boolean to specify if waiting
// for this task is needed.
// Usage of short unique IDs is supported for this operation.
func StartContainer(containers []string, asyncTask bool) ([]string, error) {
	fullIds, err := selflink.GetFullIds(containers, new(ContainersList), utils.CONTAINER)
	utils.CheckBlockingError(err)
	links := utils.CreateResLinksForContainer(fullIds)

	if len(containers) < 1 || containers[0] == "" {
		return nil, ContainersNotProvidedError
	}
	containersStartOperation := &ContainersOperation{
		Operation:     "Container.Start",
		ResourceLinks: links,
		ResourceType:  "DOCKER_CONTAINER",
	}

	return processContainersOperation(containersStartOperation, asyncTask)
}

// StopContainer stops containers by their IDs.
// As second parameter takes boolean to specify if waiting
// for this task is needed.
// Usage of short unique IDs is supported for this operation.
func StopContainer(containers []string, asyncTask bool) ([]string, error) {
	fullIds, err := selflink.GetFullIds(containers, new(ContainersList), utils.CONTAINER)
	utils.CheckBlockingError(err)
	links := utils.CreateResLinksForContainer(fullIds)

	if len(containers) < 1 || containers[0] == "" {
		return nil, ContainersNotProvidedError
	}
	containersStopOperation := &ContainersOperation{
		Operation:     "Container.Stop",
		ResourceLinks: links,
		ResourceType:  "DOCKER_CONTAINER",
	}
	return processContainersOperation(containersStopOperation, asyncTask)
}

// RemoveContainer removes containers by their IDs.
// As second parameter takes boolean to specify if waiting
// for this task is needed.
// Usage of short unique IDs is supported for this operation.
func RemoveContainer(containers []string, asyncTask bool) ([]string, error) {
	fullIds, err := selflink.GetFullIds(containers, new(ContainersList), utils.CONTAINER)
	utils.CheckBlockingError(err)
	links := utils.CreateResLinksForContainer(fullIds)

	if len(containers) < 1 || containers[0] == "" {
		return nil, ContainersNotProvidedError
	}
	containersRemoveOperation := &ContainersOperation{
		Operation:     "Container.Delete",
		ResourceLinks: links,
		ResourceType:  "DOCKER_CONTAINER",
	}

	return processContainersOperation(containersRemoveOperation, asyncTask)
}

//RemoveMany removes many containers matching specified query
//Returns boolean result if they are removing or not.
func RemoveMany(container string, asyncTask bool) ([]string, error) {
	lc := &ContainersList{}
	count, err := lc.FetchContainers(container)
	if err != nil {
		return nil, err
	}
	if count < 1 {
		return nil, ContainersNotMatchQueryError
	}
	containersRemoveManyOperation := &ContainersOperation{
		Operation:     "Container.Delete",
		ResourceLinks: lc.DocumentLinks,
		ResourceType:  "DOCKER_CONTAINER",
	}

	return processContainersOperation(containersRemoveManyOperation, asyncTask)
}

// ScaleContainer scales container by it's IDs.
// The second parameter is the new cluster size of the container.
// As third parameter takes boolean to specify if waiting
// for this task is needed.
// Usage of short unique IDs is supported for this operation.
func ScaleContainer(containerID string, scaleCount int32, asyncTask bool) (string, error) {
	fullIds, err := selflink.GetFullIds([]string{containerID}, new(ContainersList), utils.CONTAINER)
	utils.CheckBlockingError(err)
	links := utils.CreateResLinksForContainer(fullIds)
	url := config.URL + links[0]
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	container := &Container{}
	err = json.Unmarshal(respBody, container)
	utils.CheckBlockingError(err)
	contDesc := container.DescriptionLink

	url = config.URL + "/requests"
	containersScaleOperation := &ContainersOperationScale{
		ResourceDescriptionLink: contDesc,
		ResourceCount:           scaleCount,
	}
	containersScaleOperation.Operation = "CLUSTER_RESOURCE"
	containersScaleOperation.ResourceType = "DOCKER_CONTAINER"

	resLinks, err := processContainersOperation(containersScaleOperation, asyncTask)
	return strings.Join(resLinks, ","), err

}

func processContainersOperation(co ContainersOperator, asyncTask bool) ([]string, error) {
	url := urlutils.BuildUrl(urlutils.RequestBrokerService, nil, true)
	jsonBody, err := json.Marshal(co)
	utils.CheckBlockingError(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	if !asyncTask {
		resLinks, err := track.StartWaitingFromResponseBody(respBody)
		return resLinks, err
	}
	track.PrintTaskIdFromResponseBody(respBody)
	return nil, nil
}

// ExecuteCmd executes command in container.
// Usage of short unique IDs is supported for this operation.
func ExecuteCmd(container string, command string) {
	fullIds, err := selflink.GetFullIds([]string{container}, new(ContainersList), utils.CONTAINER)
	utils.CheckBlockingError(err)
	links := utils.CreateResLinksForContainer(fullIds)
	contLink := links[0]
	exec := strings.Split(command, " ")
	url := config.URL + "/exec?containerLink=" + contLink
	ch := CommandHolder{
		Command: exec,
	}
	jsonBody, err := json.Marshal(ch)
	utils.CheckBlockingError(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		fmt.Println(respErr)
	} else {
		fmt.Print(string(respBody))
	}
}

// InspectContainer returns information about container in JSON format.
func InspectContainer(id string) ([]byte, error) {
	fullIds, err := selflink.GetFullIds([]string{id}, new(ContainersList), utils.CONTAINER)
	utils.CheckBlockingError(err)
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

// GetContainer return pointer to object of type Container,
// which is fetched from Admiral by the provided ID.
func GetContainer(id string) *Container {
	fullId, err := selflink.GetFullId(id, new(ContainersList), utils.CONTAINER)
	utils.CheckBlockingError(err)
	link := utils.CreateResLinksForContainer([]string{fullId})[0]
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	utils.CheckResponse(respErr, url)
	c := &Container{}
	err = json.Unmarshal(respBody, c)
	utils.CheckBlockingError(err)
	return c
}

// GetContainerDescription return pointer to object of type ContainerDescription,
// which is fetched from Admiral by the provided ID.
func GetContainerDescription(id string) *ContainerDescription {
	link := utils.CreateResLinkForContainerDescription(id)
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	utils.CheckResponse(respErr, url)
	cd := &ContainerDescription{}
	err := json.Unmarshal(respBody, cd)
	utils.CheckBlockingError(err)
	return cd
}
