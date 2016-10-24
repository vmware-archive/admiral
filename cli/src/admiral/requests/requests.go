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
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	"admiral/client"
	"admiral/config"
	"admiral/events"
	"admiral/utils"
	"bytes"
	"strconv"
)

type TaskInfo struct {
	Stage   string `json:"stage"`
	Failure struct {
		Message string `json:"message"`
	} `json:"failure"`
}

type RequestInfo struct {
	TaskInfo                 TaskInfo `json:"taskInfo"`
	Phase                    string   `json:"phase"`
	Name                     string   `json:"name"`
	Progress                 int      `json:"progress"`
	ResourceLinks            []string `json:"resourceLinks"`
	DocumentUpdateTimeMicros int64    `json:"documentUpdateTimeMicros"`
	EventLogInfo             string   `json:"eventLogInfo"`
	EventLogLink             string   `json:"eventLogLink"`
	DocumentSelfLink         string   `json:"documentSelfLink"`
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

func (ri *RequestInfo) GetLastUpdate() string {
	then := time.Unix(0, ri.DocumentUpdateTimeMicros*int64(time.Microsecond))
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

type RequestsList struct {
	TotalCount    int32                  `json:"totalCount"`
	Documents     map[string]RequestInfo `json:"documents"`
	DocumentLinks []string               `json:"documentLinks"`
}

const (
	RequestIdAlign = "30"
	IndentAlign    = "28"

	DownAndHorizontal = "\u252c" // ┬
	UpAndRight        = "\u2514" //  └
	Horizontal        = "\u2500" // ─
	VerticalAndRight  = "\u251c" // ├
)

var (
	defaultFormat       = "%-" + RequestIdAlign + "s%-45s %-15s %-15s %s\n"
	defaultIndentFormat = "%-" + IndentAlign + "s%-45s\n"
)

func (rl *RequestsList) ClearAllRequests() {
	for i := len(rl.DocumentLinks) - 1; i >= 0; i-- {
		url := config.URL + rl.DocumentLinks[i]
		req, _ := http.NewRequest("DELETE", url, nil)
		client.ProcessRequest(req)
	}
	fmt.Println("Requests successfully cleared.")
}

func (rl *RequestsList) FetchRequests() (int, error) {
	url := config.URL + "/request-status?documentType=true&$count=false&$limit=1000&$orderby=documentExpirationTimeMicros+desc&$filter=taskInfo/stage+eq+'*'"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, rl)
	utils.CheckJson(err)
	return len(rl.DocumentLinks), nil
}

func (rl *RequestsList) PrintStartedOnly() {
	indent := VerticalAndRight + Horizontal
	lastIndent := UpAndRight + Horizontal

	fmt.Println("\t---STARTED---")
	fmt.Printf(defaultFormat, "ID", "RESOURCES", "STATUS", "SINCE", "MESSAGE")
	for i := len(rl.DocumentLinks) - 1; i >= 0; i-- {
		val := rl.Documents[rl.DocumentLinks[i]]
		if val.TaskInfo.Stage != "STARTED" {
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

func (rl *RequestsList) PrintFailedOnly() {
	indent := VerticalAndRight + Horizontal
	lastIndent := UpAndRight + Horizontal

	fmt.Println("\t---FAILED---")
	fmt.Printf(defaultFormat, "ID", "RESOURCES", "STATUS", "SINCE", "MESSAGE")
	for i := len(rl.DocumentLinks) - 1; i >= 0; i-- {
		val := rl.Documents[rl.DocumentLinks[i]]
		if val.TaskInfo.Stage != "FAILED" {
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

func (rl *RequestsList) PrintFinishedOnly() {
	indent := VerticalAndRight + Horizontal
	lastIndent := UpAndRight + Horizontal

	fmt.Println("\t---FINISHED---")
	fmt.Printf(defaultFormat, "ID", "RESOURCES", "STATUS", "SINCE", "MESSAGE")
	for i := len(rl.DocumentLinks) - 1; i >= 0; i-- {
		val := rl.Documents[rl.DocumentLinks[i]]
		if val.TaskInfo.Stage != "FINISHED" {
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

func (rl *RequestsList) PrintAll() {
	indent := VerticalAndRight + Horizontal
	lastIndent := UpAndRight + Horizontal

	fmt.Printf(defaultFormat, "ID", "RESOURCES", "STATUS", "SINCE", "MESSAGE")
	for i := len(rl.DocumentLinks) - 1; i >= 0; i-- {
		val := rl.Documents[rl.DocumentLinks[i]]
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
	utils.CheckJson(err)
	res := strings.Replace(event.Description, "\n", "", -1)
	return res
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
