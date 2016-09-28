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
	"admiral/functions"
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
	return functions.GetResourceID(ri.ResourceLinks[index])
}

func (ri *RequestInfo) GetID() string {
	return functions.GetResourceID(ri.DocumentSelfLink)
}

func (ri *RequestInfo) GetLastUpdate() string {
	then := time.Unix(0, ri.DocumentUpdateTimeMicros*int64(time.Microsecond))
	timeSinceUpdate := time.Now().Sub(then)
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

var defaultFormat = "%-40s %-45s %-15s %-10s %s\n"

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
	functions.CheckJson(err)
	return len(rl.DocumentLinks), nil
}

func (rl *RequestsList) PrintStartedOnly() {
	indent := "\u251c\u2500"
	lastIndent := "\u2514\u2500"

	fmt.Println("\t---STARTED---")
	fmt.Printf(defaultFormat, "ID", "RESOURCES", "STATUS", "SINCE", "MESSAGE")
	for i := len(rl.DocumentLinks) - 1; i >= 0; i-- {
		val := rl.Documents[rl.DocumentLinks[i]]
		if val.TaskInfo.Stage != "STARTED" {
			continue
		}
		res, failure := checkFailed(&val)
		if res {
			failure = functions.ShortString(failure, 50)
			fmt.Printf(defaultFormat, val.GetID(), val.indentFirstId(), val.TaskInfo.Stage, val.GetLastUpdate(), failure)
		} else {
			fmt.Printf(defaultFormat, val.GetID(), val.indentFirstId(), val.TaskInfo.Stage, val.GetLastUpdate(), "")
		}
		for i := 1; i < len(val.ResourceLinks); i++ {
			fmt.Printf("%-40s %-45s\n", "", indent+val.GetResourceID(i))
			if i == len(val.ResourceLinks)-1 {
				fmt.Printf("%-40s %-45s\n", "", lastIndent+val.GetResourceID(i))
			}
		}
	}
}

func (rl *RequestsList) PrintFailedOnly() {
	indent := "\u251c\u2500"
	lastIndent := "\u2514\u2500"

	fmt.Println("\t---FAILED---")
	fmt.Printf(defaultFormat, "ID", "RESOURCES", "STATUS", "SINCE", "MESSAGE")
	for i := len(rl.DocumentLinks) - 1; i >= 0; i-- {
		val := rl.Documents[rl.DocumentLinks[i]]
		if val.TaskInfo.Stage != "FAILED" {
			continue
		}
		res, failure := checkFailed(&val)
		if res {
			failure = functions.ShortString(failure, 50)
			fmt.Printf(defaultFormat, val.GetID(), val.indentFirstId(), val.TaskInfo.Stage, val.GetLastUpdate(), failure)
		} else {
			fmt.Printf(defaultFormat, val.GetID(), val.indentFirstId(), val.TaskInfo.Stage, val.GetLastUpdate(), "")
		}
		for i := 1; i < len(val.ResourceLinks); i++ {
			fmt.Printf("%-40s %-45s\n", "", indent+val.GetResourceID(i))
			if i == len(val.ResourceLinks)-1 {
				fmt.Printf("%-40s %-45s\n", "", lastIndent+val.GetResourceID(i))
			}
		}
	}
}

func (rl *RequestsList) PrintFinishedOnly() {
	indent := "\u251c\u2500"
	lastIndent := "\u2514\u2500"

	fmt.Println("\t---FINISHED---")
	fmt.Printf(defaultFormat, "ID", "RESOURCES", "STATUS", "SINCE", "MESSAGE")
	for i := len(rl.DocumentLinks) - 1; i >= 0; i-- {
		val := rl.Documents[rl.DocumentLinks[i]]
		if val.TaskInfo.Stage != "FINISHED" {
			continue
		}
		res, failure := checkFailed(&val)
		if res {
			failure = functions.ShortString(failure, 50)
			fmt.Printf(defaultFormat, val.GetID(), val.indentFirstId(), val.TaskInfo.Stage, val.GetLastUpdate(), failure)
		} else {
			fmt.Printf(defaultFormat, val.GetID(), val.indentFirstId(), val.TaskInfo.Stage, val.GetLastUpdate(), "")
		}
		for i := 1; i < len(val.ResourceLinks); i++ {
			fmt.Printf("%-40s %-45s\n", "", indent+val.GetResourceID(i))
			if i == len(val.ResourceLinks)-1 {
				fmt.Printf("%-40s %-45s\n", "", lastIndent+val.GetResourceID(i))
			}
		}
	}
}

func (rl *RequestsList) PrintAll() {
	indent := "\u251c\u2500"
	lastIndent := "\u2514\u2500"

	fmt.Printf(defaultFormat, "ID", "RESOURCES", "STATUS", "SINCE", "MESSAGE")
	for i := len(rl.DocumentLinks) - 1; i >= 0; i-- {
		val := rl.Documents[rl.DocumentLinks[i]]
		res, failure := checkFailed(&val)
		if res {
			failure = functions.ShortString(failure, 50)
			fmt.Printf(defaultFormat, val.GetID(), val.indentFirstId(), val.TaskInfo.Stage, val.GetLastUpdate(), failure)
		} else {
			fmt.Printf(defaultFormat, val.GetID(), val.indentFirstId(), val.TaskInfo.Stage, val.GetLastUpdate(), "")
		}
		for i := 1; i < len(val.ResourceLinks); i++ {
			fmt.Printf("%-40s %-45s\n", "", indent+val.GetResourceID(i))
			if i == len(val.ResourceLinks)-1 {
				fmt.Printf("%-40s %-45s\n", "", lastIndent+val.GetResourceID(i))
			}
		}
	}
}

func checkFailed(ri *RequestInfo) (bool, string) {
	if ri.TaskInfo.Stage != "FAILED" {
		return false, ""
	}

	if ri.TaskInfo.Failure.Message != "" {
		return true, ri.TaskInfo.Failure.Message
	}
	url := config.URL + ri.EventLogLink
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, _ := client.ProcessRequest(req)
	event := &events.EventInfo{}
	err := json.Unmarshal(respBody, event)
	functions.CheckJson(err)
	res := strings.Replace(event.Description, "\n", "", -1)
	return true, res
}

func (ri *RequestInfo) indentFirstId() string {
	firstIndent := "\u250c\u2500"
	if len(ri.ResourceLinks) > 1 {
		return firstIndent + ri.GetResourceID(0)
	} else if len(ri.ResourceLinks) == 1 {
		return ri.GetResourceID(0)
	} else {
		return ""
	}
}
