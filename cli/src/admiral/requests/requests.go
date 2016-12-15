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

package requests

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"admiral/client"
	"admiral/config"
	"admiral/events"
	"admiral/utils"
	"admiral/utils/selflink"
)

const (
	RequestIdAlign = "30"
	IndentAlign    = "28"

	DownAndHorizontal = "\u252c" // ┬
	UpAndRight        = "\u2514" //  └
	Horizontal        = "\u2500" // ─
	VerticalAndRight  = "\u251c" // ├

	STARTED  = "STARTED"
	FINISHED = "FINISHED"
	FAILED   = "FAILED"
	ALL      = "ALL"
)

var (
	defaultFormat       = "%-" + RequestIdAlign + "s%-45s %-15s %-15s %s\n"
	defaultIndentFormat = "%-" + IndentAlign + "s%-45s\n"
)

type TaskInfo struct {
	Stage   string `json:"stage"`
	Failure struct {
		Message string `json:"message"`
	} `json:"failure"`
}

type RequestInfo struct {
	TaskInfo                 TaskInfo    `json:"taskInfo"`
	Phase                    string      `json:"phase"`
	Name                     string      `json:"name"`
	Progress                 int         `json:"progress"`
	ResourceLinks            []string    `json:"resourceLinks"`
	DocumentUpdateTimeMicros interface{} `json:"documentUpdateTimeMicros"`
	EventLogInfo             string      `json:"eventLogInfo"`
	EventLogLink             string      `json:"eventLogLink"`
	DocumentSelfLink         string      `json:"documentSelfLink"`
}

func (ri *RequestInfo) GetResourceID(index int) string {
	if index > (len(ri.ResourceLinks) - 1) {
		return ""
	}
	return utils.GetResourceID(ri.ResourceLinks[index])
}

func (ri *RequestInfo) GetID() string {
	return utils.GetResourceID(ri.DocumentSelfLink)
}

func (ri *RequestInfo) GetDocumentUpdateTimeMicros() int64 {
	switch v := ri.DocumentUpdateTimeMicros.(type) {
	case string:
		parsed, _ := strconv.ParseInt(v, 10, 64)
		return parsed
	case int64:
		return v
	case float64:
		return int64(v)
	default:
		return 0
	}
}

func (ri *RequestInfo) GetLastUpdate() string {
	then := time.Unix(0, ri.GetDocumentUpdateTimeMicros()*int64(time.Microsecond))
	timeSinceUpdate := time.Now().Sub(then)
	if timeSinceUpdate.Hours() > 72 {
		daysAgo := int(float64(timeSinceUpdate.Hours()) / 24.0)
		return fmt.Sprintf("%d days", daysAgo)
	}
	if timeSinceUpdate.Hours() > 1 {
		return fmt.Sprintf("%d hours", int64(timeSinceUpdate.Hours()))
	}
	if timeSinceUpdate.Minutes() > 1 {
		return fmt.Sprintf("%d minutes", int64(timeSinceUpdate.Minutes()))
	}
	if timeSinceUpdate.Seconds() > 1 {
		return fmt.Sprintf("%d seconds", int64(timeSinceUpdate.Seconds()))
	}
	return "0 seconds"

}

func (ri *RequestInfo) getFirstResId() string {
	if len(ri.ResourceLinks) >= 1 {
		return ri.GetResourceID(0)
	}
	return ""

}

func (ri *RequestInfo) getLinkedId() string {
	if len(ri.ResourceLinks) <= 1 {
		return ri.GetID()
	}
	var buffer bytes.Buffer
	buffer.WriteString(ri.GetID())
	indentAlignInt, _ := strconv.Atoi(IndentAlign)
	indentLoops := indentAlignInt - len(ri.GetID())
	for i := 0; i < indentLoops; i++ {
		buffer.WriteString(Horizontal)
	}
	buffer.WriteString(DownAndHorizontal + Horizontal)
	return buffer.String()
}

type RequestsList struct {
	TotalCount    int32                  `json:"totalCount"`
	Documents     map[string]RequestInfo `json:"documents"`
	DocumentLinks []string               `json:"documentLinks"`
}

func (rl *RequestsList) GetCount() int {
	return len(rl.DocumentLinks)
}

func (rl *RequestsList) GetResource(index int) selflink.Identifiable {
	resource := rl.Documents[rl.DocumentLinks[index]]
	return &resource
}

func (rl *RequestsList) Renew() {
	*rl = RequestsList{}
}

func (rl *RequestsList) ClearAllRequests() (string, []error) {
	errs := make([]error, 0)
	for i := len(rl.DocumentLinks) - 1; i >= 0; i-- {
		url := config.URL + rl.DocumentLinks[i]
		req, _ := http.NewRequest("DELETE", url, nil)
		_, _, respErr := client.ProcessRequest(req)
		if respErr != nil {
			errs = append(errs, respErr)
		}
	}
	output := "Requests successfully cleared."
	return output, errs
}

func (rl *RequestsList) FetchRequests() (int, error) {
	url := config.URL + "/request-status?documentType=true&$count=false&$limit=1000&$orderby=documentExpirationTimeMicros+desc&$filter=taskInfo/stage+eq+'*'"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, rl)
	utils.CheckBlockingError(err)
	return len(rl.DocumentLinks), nil
}

func (rl *RequestsList) Print(what string) {
	indent := VerticalAndRight + Horizontal
	lastIndent := UpAndRight + Horizontal

	fmt.Printf(defaultFormat, "ID", "RESOURCES", "STATUS", "SINCE", "MESSAGE")
	for i := len(rl.DocumentLinks) - 1; i >= 0; i-- {
		val := rl.Documents[rl.DocumentLinks[i]]
		if val.TaskInfo.Stage != what && what != "ALL" {
			continue
		}
		failure := checkFailed(&val)
		failure = utils.ShortString(failure, 50)

		fmt.Printf(defaultFormat, val.getLinkedId(), val.getFirstResId(), val.TaskInfo.Stage, val.GetLastUpdate(), failure)
		for i := 1; i < len(val.ResourceLinks); i++ {
			fmt.Printf(defaultIndentFormat, "", indent+val.GetResourceID(i))
			if i == len(val.ResourceLinks)-1 {
				fmt.Printf(defaultIndentFormat, "", lastIndent+val.GetResourceID(i))
			}
		}
	}
}

func RemoveRequestID(id string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(RequestsList), utils.REQUEST)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForRequest(fullId)
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	return fullId, respErr
}

type Resource struct {
	ID    string `json:"ResourceID,omitempty"`
	Type  string `json:"Type,omitempty"`
	Exist bool   `json:"Exist"`
}
type InspectedRequest struct {
	ID        string      `json:"RequestID,omitempty"`
	Status    string      `json:"Status,omitempty"`
	Since     string      `json:"Since,omitempty"`
	Message   string      `json:"Message,omitempty"`
	Resources []*Resource `json:"Resources,omitempty"`
}

func InspectRequestID(id string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(RequestsList), utils.REQUEST)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForRequest(fullId)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	ri := &RequestInfo{}
	err = json.Unmarshal(respBody, ri)
	utils.CheckBlockingError(err)

	inspectedRequest := &InspectedRequest{}
	inspectedRequest.ID = ri.GetID()
	inspectedRequest.Status = ri.TaskInfo.Stage
	inspectedRequest.Since = ri.GetLastUpdate()
	inspectedRequest.Message = checkFailed(ri)
	inspectedRequest.Resources = make([]*Resource, 0)

	for _, resLink := range ri.ResourceLinks {
		resource := getResourceInfo(resLink)
		if resource != nil {
			inspectedRequest.Resources = append(inspectedRequest.Resources, resource)
		}
	}

	jsonBody, _ := json.MarshalIndent(inspectedRequest, "", "    ")
	return string(jsonBody), nil
}

func getResourceInfo(resLink string) *Resource {
	if resLink == "" {
		return nil
	}
	resUrl := config.URL + resLink
	req, _ := http.NewRequest("GET", resUrl, nil)
	resp, _, _ := client.ProcessRequest(req)
	resource := &Resource{}
	linkArr := strings.Split(resLink, "/")
	switch linkArr[2] {
	case "containers":
		resource.Type = "Container"
	case "composite-components":
		resource.Type = "Application"
	case "container-networks":
		resource.Type = "Network"
	case "compute":
		resource.Type = "Host"
	default:
		resource.Type = "Unknown"
	}
	resource.ID = linkArr[3]
	if resp.StatusCode != 200 {
		resource.Exist = false
		return resource
	}
	resource.Exist = true
	return resource
}

func checkFailed(ri *RequestInfo) string {
	if ri.TaskInfo.Stage != "FAILED" {
		return ""
	}

	if ri.TaskInfo.Failure.Message != "" {
		return ri.TaskInfo.Failure.Message
	}
	url := config.URL + ri.EventLogLink
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, _ := client.ProcessRequest(req)
	event := &events.EventInfo{}
	err := json.Unmarshal(respBody, event)
	utils.CheckBlockingError(err)
	res := strings.Replace(event.Description, "\n", "", -1)
	return res
}
