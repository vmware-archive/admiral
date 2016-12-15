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
	"admiral/utils/selflink"
	"admiral/utils/urlutils"
)

type App struct {
	Name                     string   `json:"name"`
	CompositeDescriptionLink string   `json:"compositeDescriptionLink"`
	DocumentSelfLink         string   `json:"documentSelfLink"`
	ComponentLinks           []string `json:"componentLinks"`
}

// IsContainer returns boolean which specify if the
// component at the given index is container.
func (a *App) GetComponentResourceType(index int) utils.ResourceType {
	link := a.ComponentLinks[index]
	if strings.Contains(link, "/containers/") {
		return utils.CONTAINER
	} else if strings.Contains(link, "/closures/") {
		return utils.CLOSURE
	} else if strings.Contains(link, "/container-networks/") {
		return utils.NETWORK
	}
	return -1
}

// GetID returns the ID by getting the last part
// of the document selflink if split by slash.
func (a *App) GetID() string {
	return utils.GetResourceID(a.DocumentSelfLink)
}

// GetContainersCount returns the count of
// the components that are containers.
func (a *App) GetContainersCount() int {
	count := 0
	for _, link := range a.ComponentLinks {
		if strings.Contains(link, "/containers/") {
			count++
		}
	}
	return count
}

// GetNetworksCount return the count of
// the components tat are networks.
func (a *App) GetNetworksCount() int {
	count := 0
	for _, link := range a.ComponentLinks {
		if strings.Contains(link, "/container-networks/") {
			count++
		}
	}
	return count
}

func (a *App) GetClosuresCount() int {
	count := 0
	for _, link := range a.ComponentLinks {
		if strings.Contains(link, "/closures/") {
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

// GetCount returns the count of fetched applications.
// It is used to implement the interface selflink.ResourceList
func (la *ListApps) GetCount() int {
	return len(la.DocumentLinks)
}

// GetResource returns resource at the specified index,
// which resource implements the interface selflink.Identifiable.
func (la *ListApps) GetResource(index int) selflink.Identifiable {
	resource := la.Documents[la.DocumentLinks[index]]
	return &resource
}

func (la *ListApps) Renew() {
	*la = ListApps{}
}

// FetchApps makes REST call to populate ListApps object
// with Apps. The url of this call is /resources/composite-components/
func (la *ListApps) FetchApps(queryF string) (int, error) {
	cqm := urlutils.GetCommonQueryMap()
	if strings.TrimSpace(queryF) != "" {
		cqm["$filter"] = fmt.Sprintf("ALL_FIELDS+eq+'*%s*'", queryF)
	}
	url := urlutils.BuildUrl(urlutils.CompositeComponent, cqm, true)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, la)
	utils.CheckBlockingError(err)
	return len(la.DocumentLinks), nil
}

// GetOutputStringWithoutContainers returns raw string with information
// about applications only. It is used from "app ls" command, and
// this string requires formatting before printing it to the console.
func (listApps *ListApps) GetOutputStringWithoutContainers() string {
	if listApps.GetCount() < 1 {
		return utils.NoElementsFoundMessage
	}
	var buffer bytes.Buffer
	buffer.WriteString("ID\tNAME\tCONTAINERS\tNETWORKS\tCLOSURES")
	buffer.WriteString("\n")
	for _, link := range listApps.DocumentLinks {
		app := listApps.Documents[link]
		output := utils.GetTabSeparatedString(app.GetID(), app.Name, app.GetContainersCount(), app.GetNetworksCount(), app.GetClosuresCount())
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

// GetOutputStringWithContainers is similar to GetOutputStringWithoutContainers,
// but it also contains the components inside the application.
// Currently is not being used because "app list" with containers is disabled.
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
		output := utils.GetTabSeparatedString(app.GetID(), app.Name)
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
			utils.CheckBlockingError(err)
			output = utils.GetTabSeparatedString(indent+strings.Join(container.Names, " "), container.Address,
				container.PowerState, container.GetCreated(), container.GetStarted(), container.Ports)
			buffer.WriteString(output)
			buffer.WriteString("\n")
		}
	}
	return strings.TrimSpace(buffer.String())
}
