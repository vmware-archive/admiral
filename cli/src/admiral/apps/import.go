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

package apps

import (
	"bytes"
	"errors"
	"io/ioutil"
	"net/http"
	"os"
	"strings"

	"admiral/auth"
	"admiral/client"
	"admiral/config"
	"admiral/functions"
)

func Import(dirF string) (string, error) {
	importFile, err := ioutil.ReadFile(dirF)
	functions.CheckFile(err)

	url := config.URL + "/resources/composite-templates"

	token, from := auth.GetAuthToken()
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(importFile))
	req.Header.Set("x-xenon-auth-token", token)
	req.Header.Set("content-type", "application/yaml")

	resp, err := client.NetClient.Do(req)
	functions.CheckResponse(err)
	functions.CheckVerboseResponse(resp)
	respBody, _ := ioutil.ReadAll(resp.Body)
	//Check for authentication error.
	isAuth := auth.IsAuthorized(respBody, from)
	if !isAuth {
		os.Exit(-1)
	}

	if resp.StatusCode != 200 {
		return "", errors.New("Error occured when importing template.")
	}

	link := resp.Header.Get("Location")
	id := strings.Replace(link, "/resources/composite-descriptions/", "", -1)
	return id, nil
}
