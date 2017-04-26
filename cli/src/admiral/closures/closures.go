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
	"admiral/common"
	"admiral/common/base_types"
	"admiral/common/utils"
	"admiral/common/utils/selflink_utils"
	"admiral/common/utils/uri_utils"
	"admiral/config"
)

type Closure struct {
	base_types.ServiceDocument

	State string `json:"state"`
	Name  string `json:"name"`
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

func (cl *ClosureList) GetResource(index int) selflink_utils.Identifiable {
	resource := cl.Documents[cl.DocumentLinks[index]]
	return &resource
}

func (cl *ClosureList) Renew() {
	*cl = ClosureList{}
}

func (cl *ClosureList) FetchClosures() (int, error) {
	url := uri_utils.BuildUrl(uri_utils.Closure, uri_utils.GetCommonQueryMap(), true)
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
		return selflink_utils.NoElementsFoundMessage
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

func RemoveClosure(idOrName string) (string, error) {
	fullId, err := selflink_utils.GetFullId(idOrName, new(ClosureList), common.CLOSURE)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForClosure(fullId)
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	return fullId, respErr
}

func GetClosure(id string) *Closure {
	fullId, err := selflink_utils.GetFullId(id, new(ClosureList), common.CLOSURE)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForClosure(fullId)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, _ := client.ProcessRequest(req)
	closure := &Closure{}
	err = json.Unmarshal(respBody, closure)
	utils.CheckBlockingError(err)
	return closure
}
