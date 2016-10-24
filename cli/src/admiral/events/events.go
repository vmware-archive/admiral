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
	"admiral/utils"
	"bytes"
	"fmt"
	"time"
)

type EventInfo struct {
	EventLogType             string `json:"eventLogType"`
	Description              string `json:"description"`
	DocumentUpdateTimeMicros int64  `json:"documentUpdateTimeMicros"`
}

func (ei *EventInfo) GetLastUpdate() string {
	then := time.Unix(0, ei.DocumentUpdateTimeMicros*int64(time.Microsecond))
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

func (ei *EventInfo) GetDescription() string {
	return strings.TrimSpace(ei.Description)
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
	utils.CheckJson(err)
	return len(el.Documents), nil
}

//Print already fetched events.
func (el *EventList) GetOutputString() string {
	if el.TotalCount < 1 {
		return "No elements found."
	}
	var buffer bytes.Buffer
	header := fmt.Sprintf("%-15s %s\n", "SINCE", "DESCRIPTION")
	buffer.WriteString(header)
	for _, val := range el.Documents {
		output := fmt.Sprintf("%-15s %s", val.GetLastUpdate(), val.GetDescription())
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

//Clear all events.
func (el *EventList) ClearAllEvent() {
	for link := range el.Documents {
		url := config.URL + link
		req, _ := http.NewRequest("DELETE", url, nil)
		client.ProcessRequest(req)
	}
}
