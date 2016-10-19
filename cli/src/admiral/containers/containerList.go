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
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/utils"
)

type ListContainers struct {
	TotalCount    int64                `json:"totalCount"`
	Documents     map[string]Container `json:"documents"`
	DocumentLinks []string             `json:"documentLinks"`
}

//FetchContainers fetches containers by given query which is passed as parameter.
//In case you want to fetch all containers, pass empty string as parameter.
//The return result is the count of fetched containers.
func (lc *ListContainers) FetchContainers(queryF string) (int, error) {
	url := config.URL + "/resources/containers?documentType=true&$count=true&$limit=10000&$orderby=documentSelfLink+asc"

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
	err := json.Unmarshal(respBody, lc)
	utils.CheckJson(err)

	systemCount := 0

	for _, c := range lc.Documents {
		if c.System {
			systemCount++
		}
	}

	count := len(lc.DocumentLinks)

	return count - systemCount, nil
}

//Print is printing the containers to the console. It takes boolean
//parameter. If it's true will print all the containers. If it's false
//will print only the running containers.
func (lc *ListContainers) GetOutputString(allContainers bool) string {
	nameLen := 38
	var buffer bytes.Buffer
	buffer.WriteString("ID\tNAME\tADDRESS\tSTATUS\tCREATED\t[HOST:CONTAINER]\tEXTERNAL ID")
	buffer.WriteString("\n")
	for _, link := range lc.DocumentLinks {
		val := lc.Documents[link]
		if val.System {
			continue
		}
		if allContainers {
			name := utils.ShortString(strings.Join(val.Names[0:1], ""), nameLen)
			output := utils.GetFormattedString(val.GetID(), name, val.Address, val.GetStatus(),
				val.GetCreated(), val.GetPorts(), val.GetExternalID())
			buffer.WriteString(output)
			buffer.WriteString("\n")
		} else {
			if val.PowerState == "RUNNING" {
				name := utils.ShortString(strings.Join(val.Names[0:1], ""), nameLen)
				output := utils.GetFormattedString(val.GetID(), name, val.Address, val.GetStatus(),
					val.GetCreated(), val.GetPorts(), val.GetExternalID())
				buffer.WriteString(output)
				buffer.WriteString("\n")
			}
		}
	}

	return strings.TrimSpace(buffer.String())
}

//Function to get container description if the name is equal to one passed in parameter.
func (lc *ListContainers) GetContainerDescription(name string) string {
	for _, cont := range lc.Documents {
		if name == cont.Names[0] {
			return cont.DescriptionLink
		}
	}
	return ""
}

//Function to get container self link if name is equal to one passed in parameter.
func (lc *ListContainers) GetContainerLink(name string) string {
	for _, link := range lc.DocumentLinks {
		val := lc.Documents[link]
		if name == val.Names[0] {
			return link
		}
	}
	return ""
}

//Function to verify if the given container exists.
//Return result is boolean which is true if it exists and false if it doesn't exist.
func (lc *ListContainers) VerifyExistingContainer(names []string) bool {
	for _, containerName := range names {
		for _, val := range lc.Documents {
			for _, currName := range val.Names {
				if currName == containerName {
					return true
				}
			}
		}
	}
	return false
}
