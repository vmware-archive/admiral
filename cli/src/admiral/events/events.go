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

package events

import (
	"encoding/json"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/functions"
	"bytes"
)

type EventInfo struct {
	EventLogType string `json:"eventLogType"`
	Description  string `json:"description"`
}

type EventList struct {
	TotalCount int32                `json:"totalCount"`
	Documents  map[string]EventInfo `json:"documents"`
}

//FetchEvents fetches all events and returns their count.
func (el *EventList) FetchEvents() (int, error) {
	url := config.URL + "/resources/event-logs?documentType=true&$count=false&$limit=1000&$orderby=documentExpirationTimeMicros+desc"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, el)
	functions.CheckJson(err)
	return len(el.Documents), nil
}

//Print already fetched events.
func (el *EventList) GetOutputString() string {
	if el.TotalCount < 1 {
		return "No elements found."
	}
	var buffer bytes.Buffer
	for _, val := range el.Documents {
		description := strings.TrimSpace(val.Description)
		output := functions.GetFormattedString(val.EventLogType, description)
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

//Clear all events.
func (el *EventList) ClearAllEvent() {
	for link, _ := range el.Documents {
		url := config.URL + link
		req, _ := http.NewRequest("DELETE", url, nil)
		client.ProcessRequest(req)
	}
}
