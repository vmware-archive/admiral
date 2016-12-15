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
	"net/http"

	"admiral/client"
	"admiral/containers"
	"admiral/utils"
	"admiral/utils/selflink"
	"admiral/utils/urlutils"
)

type LogResponse struct {
	Logs string `json:"logs"`
}

func GetLog(id string, since int) (string, error) {
	fullId, err := selflink.GetFullId(id, new(containers.ContainersList), utils.CONTAINER)
	utils.CheckBlockingError(err)

	queryMap := make(map[string]interface{})
	queryMap["id"] = fullId
	queryMap["since"] = since
	url := urlutils.BuildUrl(urlutils.ContainerLogs, queryMap, true)

	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	lresp := &LogResponse{}
	err = json.Unmarshal(respBody, lresp)
	utils.CheckBlockingError(err)
	log, err := base64.StdEncoding.DecodeString(lresp.Logs)
	if err != nil {
		return "", err
	}
	return string(log), nil
}
