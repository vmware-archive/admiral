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
	"io/ioutil"
	"net/http"
	"os"
	"strings"

	"admiral/auth"
	"admiral/client"
	"admiral/config"
	"admiral/functions"
	"admiral/templates"
)

func Export(id, dirF, format string) (string, error) {
	file, err := verifyFile(dirF)
	if err != nil {
		return "", err
	}
	url := config.URL + "/resources/composite-templates?selfLink=" + id
	if format == "docker" {
		url = url + "&format=docker"
	}
	req, _ := http.NewRequest("GET", url, nil)
	token, from := auth.GetAuthToken()
	functions.CheckVerboseRequest(req)
	req.Header.Set("content-type", "application/json")
	req.Header.Set("x-xenon-auth-token", token)
	resp, err := client.NetClient.Do(req)
	functions.CheckResponse(err)
	functions.CheckVerboseResponse(resp)
	respBody, err := ioutil.ReadAll(resp.Body)
	functions.CheckJson(err)
	defer resp.Body.Close()
	isAuth := auth.IsAuthorized(respBody, from)
	if !isAuth {
		os.Remove(dirF)
		os.Exit(-1)
	}
	if err != nil {
		os.Remove(dirF)
		return "", err
	}
	_, err = file.Write(respBody)

	if err != nil {
		os.Remove(dirF)
		return "", err
	}
	return id, nil
}

func getTemplateID(name string) (string, bool) {
	listTemplates := &templates.TemplatesList{}
	listTemplates.FetchTemplates("")
	var rawSelfLink string
	for _, template := range listTemplates.Results {
		if template.Name == name {
			rawSelfLink = *template.DocumentSelfLink
			break
		}
	}
	if rawSelfLink == "" {
		return "", false
	}
	selfLink := strings.Replace(rawSelfLink, "/resources/composite-descriptions/", "", 1)
	return selfLink, true
}

//Function to verify if file can be created.
//Returns the file and result of verification
func verifyFile(dirF string) (*os.File, error) {
	file, err := os.Create(dirF)
	return file, err
}
