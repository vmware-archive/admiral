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

package closures

import (
	"bytes"
	"encoding/json"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/utils"
	"admiral/utils/selflink"
)

type Closure struct {
	State            string `json:"state"`
	Name             string `json:"name"`
	DocumentSelfLink string `json:"documentSelfLink"`
}

func (c *Closure) GetID() string {
	return utils.GetResourceID(c.DocumentSelfLink)
}

type ClosureList struct {
	Documents     map[string]Closure `json:"documents"`
	DocumentLinks []string           `json:"documentLinks"`
}

func (cl *ClosureList) GetCount() int {
	return len(cl.Documents)
}

func (cl *ClosureList) GetResource(index int) selflink.Identifiable {
	resource := cl.Documents[cl.DocumentLinks[index]]
	return &resource
}

func (cl *ClosureList) FetchClosures() (int, error) {
	url := config.URL + "/resources/closures?documentType=true&$count=true&$limit=19&$orderby="
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, cl)
	utils.CheckBlockingError(err)
	return cl.GetCount(), nil
}

func (cl *ClosureList) GetOutputString() string {
	if cl.GetCount() < 1 {
		return utils.NoElementsFoundMessage
	}
	var buffer bytes.Buffer
	buffer.WriteString("ID\tNAME\tSTATUS\n")
	for _, link := range cl.DocumentLinks {
		val := cl.Documents[link]
		buffer.WriteString(utils.GetTabSeparatedString(val.GetID(), val.Name, val.State))
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

func RemoveClosureID(id string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(ClosureList), utils.CLOSURE)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForClosure(fullId)
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	return id, respErr
}

func GetClosure(id string) *Closure {
	fullId, err := selflink.GetFullId(id, new(ClosureList), utils.CLOSURE)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForClosure(fullId)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, _ := client.ProcessRequest(req)
	closure := &Closure{}
	err = json.Unmarshal(respBody, closure)
	utils.CheckBlockingError(err)
	return closure
}

type ClosureDescription struct {
	Runtime string `json:"runtime"`
	Name    string `json:"name"`
}

func GetClosureDescription(id string) *ClosureDescription {
	url := config.URL + utils.CreateResLinkForClosureDescription(id)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, _ := client.ProcessRequest(req)
	closureDescription := &ClosureDescription{}
	err := json.Unmarshal(respBody, closureDescription)
	utils.CheckBlockingError(err)
	return closureDescription
}
