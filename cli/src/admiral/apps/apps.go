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

package apps

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/containers"
	"admiral/utils"
)

type App struct {
	Name                     string   `json:"name"`
	CompositeDescriptionLink string   `json:"compositeDescriptionLink"`
	DocumentSelfLink         string   `json:"documentSelfLink"`
	ComponentLinks           []string `json:"componentLinks"`
}

func (a *App) GetID() string {
	return utils.GetResourceID(a.DocumentSelfLink)
}

func (a *App) GetContainersCount() int {
	count := 0
	for _, link := range a.ComponentLinks {
		if strings.Contains(link, "/containers/") {
			count++
		}
	}
	return count
}

func (a *App) GetNetworksCount() int {
	count := 0
	for _, link := range a.ComponentLinks {
		if strings.Contains(link, "/container-networks/") {
			count++
		}
	}
	return count
}

type ListApps struct {
	TotalCount    int32          `json:"totalCount"`
	DocumentLinks []string       `json:"documentLinks"`
	Documents     map[string]App `json:"documents"`
}

func (la *ListApps) FetchApps(queryF string) (int, error) {
	url := config.URL + "/resources/composite-components?documentType=true&$count=true&$limit=21&$orderby=documentSelfLink+asc"
	var query string
	if strings.TrimSpace(queryF) != "" {
		query = fmt.Sprintf("&$filter=ALL_FIELDS+eq+'*%s*'", queryF)
		url = url + query
	}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, la)
	utils.CheckJson(err)
	return len(la.DocumentLinks), nil
}

//Function to get links of applications, matching the name from argument.
func (la *ListApps) GetMatchingNames(name string) []string {
	links := make([]string, 0)
	for link, app := range la.Documents {
		if app.Name == name {
			links = append(links, link)
		}
	}
	return links
}

//Function to print active applications without the containers in the apps.
func (listApps *ListApps) GetOutputStringWithoutContainers() string {
	if listApps.TotalCount < 1 {
		return "No elements found."
	}
	var buffer bytes.Buffer
	buffer.WriteString("ID\tNAME\tCONTAINERS\tNETWORKS")
	buffer.WriteString("\n")
	for _, link := range listApps.DocumentLinks {
		app := listApps.Documents[link]
		output := utils.GetFormattedString(app.GetID(), app.Name, app.GetContainersCount(), app.GetNetworksCount())
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

//Function to print the active applications with the containers in the apps.
func (listApps *ListApps) GetOutputStringWithContainers() string {
	if listApps.TotalCount < 1 {
		return "No elements found."
	}
	var buffer bytes.Buffer
	indent := "\u251c\u2500\u2500"
	url := config.URL
	buffer.WriteString("ID\tNAME")
	buffer.WriteString("\n")
	for _, app := range listApps.Documents {
		output := utils.GetFormattedString(app.GetID(), app.Name)
		buffer.WriteString(output)
		buffer.WriteString("\n")
		if len(app.ComponentLinks) < 1 {
			continue
		}
		output = indent + "NAME\tADDRESS\tSTATUS\tCREATED\tSTARTED\t[HOST:CONTAINER]"
		buffer.WriteString(output)
		buffer.WriteString("\n")
		for _, cntr := range app.ComponentLinks {
			containerUrl := url + cntr
			container := &containers.Container{}
			req, _ := http.NewRequest("GET", containerUrl, nil)
			_, respBody, _ := client.ProcessRequest(req)
			err := json.Unmarshal(respBody, container)
			utils.CheckJson(err)
			output = utils.GetFormattedString(indent+strings.Join(container.Names, " "), container.Address,
				container.PowerState, container.GetCreated(), container.GetStarted(), container.Ports)
			buffer.WriteString(output)
			buffer.WriteString("\n")
		}
	}
	return strings.TrimSpace(buffer.String())
}
