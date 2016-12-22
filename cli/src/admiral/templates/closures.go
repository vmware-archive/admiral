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

package templates

import (
	"encoding/json"
	"net/http"

	"admiral/client"
	"admiral/config"
	"admiral/utils"
	"admiral/utils/selflink"
	"admiral/utils/urlutils"
	"bytes"
	"strings"
)

type ClosureDescription struct {
	Runtime          string         `json:"runtime"`
	Name             string         `json:"name"`
	Source           string         `json:"source"`
	Description      string         `json:"description"`
	Resources        map[string]int `json:"resources"`
	DocumentSelfLink string         `json:"documentSelfLink"`
}

func (cd *ClosureDescription) GetID() string {
	return utils.GetResourceID(cd.DocumentSelfLink)
}

func (cd *ClosureDescription) GetMemory() int {
	if val, ok := cd.Resources["ramMB"]; ok {
		return val
	}
	return 0
}

func (cd *ClosureDescription) GetTimeout() int {
	if val, ok := cd.Resources["timeoutSeconds"]; ok {
		return val
	}
	return 0
}

type ClosureDescriptionList struct {
	Documents     map[string]ClosureDescription `json:"documents"`
	DocumentLinks []string                      `json:"documentLinks"`
}

func (cdl *ClosureDescriptionList) GetCount() int {
	return len(cdl.DocumentLinks)
}

func (cdl *ClosureDescriptionList) GetResource(index int) selflink.Identifiable {
	resource := cdl.Documents[cdl.DocumentLinks[index]]
	return &resource
}

func (cdl *ClosureDescriptionList) Renew() {
	*cdl = ClosureDescriptionList{}
}

func (cdl *ClosureDescriptionList) FetchClosures() (int, error) {
	url := urlutils.BuildUrl(urlutils.ClosureDescription, urlutils.GetCommonQueryMap(), true)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, cdl)
	utils.CheckBlockingError(err)
	return len(cdl.Documents), nil
}

func (cdl *ClosureDescriptionList) GetOutputString() string {
	var buffer bytes.Buffer
	if cdl.GetCount() < 1 {
		return utils.NoElementsFoundMessage
	}
	buffer.WriteString("ID\tNAME\tRUNTIME\tMEMORY(MB)\tTIMEOUT(s)\tDESCRIPTION\n")
	for _, link := range cdl.DocumentLinks {
		val := cdl.Documents[link]
		output := utils.GetTabSeparatedString(val.GetID(), val.Name, val.Runtime, val.GetMemory(),
			val.GetTimeout(), utils.ShortString(val.Description, 50))
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

func RemoveClosureDescription(idOrName string) (string, error) {
	fullId, err := selflink.GetFullId(idOrName, new(ClosureDescriptionList), utils.CLOSURE_DESCRIPTION)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForClosureDescription(fullId)
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return fullId, nil
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
