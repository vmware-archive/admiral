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
	"io/ioutil"
	"net/http"
	"os"
	"strings"

	"admiral/auth"
	"admiral/client"
	"admiral/config"
	"admiral/functions"
	"admiral/track"
)

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
	links := functions.CreateResLinksForContainer(containers)

	if len(containers) < 1 || containers[0] == "" {
		return nil, errors.New("Enter atleast container.")
	}
	newStart := OperationContainer{
		Operation:     "Container.Start",
		ResourceLinks: links,
		ResourceType:  "DOCKER_CONTAINER",
	}

	jsonBody, err := json.Marshal(newStart)
	functions.CheckJson(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode == 200 {
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
		resourcesIDs := functions.GetResourceIDs(resLinks)
		return resourcesIDs, err
	}
	return nil, errors.New("Error occured when starting container.")
}

//Function to stop container by it's name.
//Returns boolean result if it stopping or not.
func StopContainer(containers []string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	links := functions.CreateResLinksForContainer(containers)

	newStop := OperationContainer{
		Operation:     "Container.Stop",
		ResourceLinks: links,
		ResourceType:  "DOCKER_CONTAINER",
	}

	jsonBody, err := json.Marshal(newStop)

	functions.CheckJson(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()

	if resp.StatusCode == 200 {
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
		resourcesIDs := functions.GetResourceIDs(resLinks)
		return resourcesIDs, err
	}
	return nil, errors.New("Error occured when stopping container.")
}

//Function to remove container by it's name.
//Returns boolean result if it removing or not.
func RemoveContainer(containers []string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	links := functions.CreateResLinksForContainer(containers)

	newRemoveContainer := OperationContainer{
		Operation:     "Container.Delete",
		ResourceLinks: links,
		ResourceType:  "DOCKER_CONTAINER",
	}

	jsonBody, err := json.Marshal(newRemoveContainer)

	functions.CheckJson(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()

	if resp.StatusCode == 200 {
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
		resourcesIDs := functions.GetResourceIDs(resLinks)
		return resourcesIDs, err
	}
	return nil, errors.New("Error occured when removing container.")
}

//Function to remove many containers matching specified query
//Returns boolean result if they are removing or not.
func RemoveMany(container string, asyncTask bool) ([]string, error) {
	lc := &ListContainers{}
	lc.FetchContainers(container)
	if len(lc.DocumentLinks) < 1 {
		return nil, errors.New("No containers found to match the query.")
	}
	url := config.URL + "/requests"
	newRemoveContainer := OperationContainer{
		Operation:     "Container.Delete",
		ResourceLinks: lc.DocumentLinks,
		ResourceType:  "DOCKER_CONTAINER",
	}

	jsonBody, err := json.Marshal(newRemoveContainer)

	functions.CheckJson(err)

	token, from := auth.GetAuthToken()
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	functions.CheckVerboseRequest(req)
	req.Header.Set("content-type", "application/json")
	req.Header.Set("x-xenon-auth-token", token)

	resp, err := client.NetClient.Do(req)
	functions.CheckResponse(err)
	functions.CheckVerboseResponse(resp)
	respBody, _ := ioutil.ReadAll(resp.Body)
	//Check for authentication error.
	isAuth := auth.IsAuthorized(respBody, from)
	if !isAuth {
		os.Exit(-1)
	}
	defer resp.Body.Close()

	if resp.StatusCode == 200 {
		taskStatus := &track.OperationResponse{}
		_ = json.Unmarshal(respBody, taskStatus)
		taskStatus.PrintTracerId()
		if !asyncTask {
			resLinks, err = track.Wait(taskStatus.GetTracerId())
		} else {
			resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
			if len(resLinks) < 1 {
				return functions.GetResourceIDs(lc.DocumentLinks), err
			}
		}
		resourcesIDs := functions.GetResourceIDs(resLinks)
		return resourcesIDs, err
	}
	return nil, errors.New("Error occured when removing containers.")
}

//Function to execute command inside container.
func ExecuteCmd(container string, execF string) {
	contLink := functions.CreateResLinksForContainer([]string{container})[0]
	exec := strings.Split(execF, " ")
	url := config.URL + "/exec?containerLink=" + contLink
	ch := CommandHolder{
		Command: exec,
	}
	jsonBody, err := json.Marshal(ch)
	functions.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	fmt.Print(string(respBody))
}

//Function to scale container by it's name with some count provided as parameter.
func ScaleContainer(containerID string, scaleCount int32, asyncTask bool) (string, error) {
	url := config.URL + functions.CreateResLinksForContainer([]string{containerID})[0]
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody := client.ProcessRequest(req)
	container := &Container{}
	err := json.Unmarshal(respBody, container)
	functions.CheckJson(err)
	contDesc := container.DescriptionLink

	url = config.URL + "/requests"
	scale := OperationScale{
		Operation:               "CLUSTER_RESOURCE",
		ResourceDescriptionLink: contDesc,
		ResourceType:            "DOCKER_CONTAINER",
		ResourceCount:           scaleCount,
	}

	scaleJson, err := json.Marshal(scale)
	functions.CheckJson(err)

	req, _ = http.NewRequest("POST", url, bytes.NewBuffer(scaleJson))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()

	if resp.StatusCode == 200 {
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
	return "", errors.New("Error occured when removing container.")
}

//Function to get information about container in JSON format.
func InspectContainer(id string) (string, error) {
	url := config.URL + functions.CreateResLinksForContainer([]string{id})[0]
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody := client.ProcessRequest(req)
	container := &Container{}
	err := json.Unmarshal(respBody, container)
	functions.CheckJson(err)
	return container.StringJson(), nil
}

func GetContainerLinks(name string) []string {
	lc := &ListContainers{}
	lc.FetchContainers(name)
	links := make([]string, 0)
	for i := range lc.DocumentLinks {
		val := lc.Documents[lc.DocumentLinks[i]]
		if val.Names[0] == name {
			links = append(links, lc.DocumentLinks[i])
		}
	}
	return links
}

type RunContainer struct {
	ResourceDescriptionLink string `json:"resourceDescriptionLink"`
	ResourceType            string `json:"resourceType"`
}

type OperationScale struct {
	Operation               string `json:"operation"`
	ResourceDescriptionLink string `json:"resourceDescriptionLink"`
	ResourceType            string `json:"resourceType"`
	ResourceCount           int32  `json:"resourceCount"`
}
