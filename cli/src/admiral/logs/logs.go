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

package logs

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"

	"admiral/client"
	"admiral/config"
	"admiral/functions"
)

type LogResponse struct {
	Logs string `json:"logs"`
}

func GetLog(contName, since string) {
	id := "id=" + contName
	sinceQ := "&since=" + since

	url := config.URL + "/resources/container-logs?" + id + sinceQ
	lresp := &LogResponse{}

	req, _ := http.NewRequest("GET", url, nil)
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	err := json.Unmarshal(respBody, lresp)
	functions.CheckJson(err)
	log, err := base64.StdEncoding.DecodeString(lresp.Logs)
	if err != nil {
		fmt.Println(err.Error())
	}
	fmt.Println(string(log))
}
