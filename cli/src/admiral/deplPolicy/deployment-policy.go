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

package deplPolicy

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/functions"
)

var (
	duplMsg    = "Duplicates with that name found. Please provide the ID for the specific deployment policy."
	notFound   = "Deployment policy not found."
	defaultMsg = "Duplicates with that name found. Please provide the ID for the specific deployment policy."
)

type DeploymentPolicy struct {
	Name             string  `json:"name,omitempty"`
	Description      string  `json:"description,omitempty"`
	DocumentSelfLink *string `json:"documentSelfLink,omitempty"`
}

//GetID returns the ID of the deployment policy as string.
func (dp *DeploymentPolicy) GetID() string {
	return strings.Replace(*dp.DocumentSelfLink, "/resources/deployment-policies/", "", -1)
}

type DeploymentPolicyList struct {
	DocumentLinks []string                    `json:"documentLinks"`
	Documents     map[string]DeploymentPolicy `json:"documents"`
}

//FetchDP fetches existing deployment policies and returns their count.
func (dpl *DeploymentPolicyList) FetchDP() (int, error) {
	url := config.URL + "/resources/deployment-policies?expand"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, dpl)
	functions.CheckJson(err)
	return len(dpl.DocumentLinks), nil
}

//Print prints already fetched deployment policies.
func (dpl *DeploymentPolicyList) GetOutputString() string {
	if len(dpl.DocumentLinks) < 1 {
		return "No elements found."
	}
	var buffer bytes.Buffer
	buffer.WriteString("ID\tNAME\tDESCRIPTION")
	buffer.WriteString("\n")
	for _, link := range dpl.DocumentLinks {
		val := dpl.Documents[link]
		output := functions.GetFormattedString(val.GetID(), val.Name, val.Description)
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

//RemoveDP removes deployment policy by name. Returns the ID of the removed
//deployment policy and error which is != nil if none or more than one
//deployment policies are found or if the response code is different from 200.
func RemoveDP(name string) (string, error) {
	links := GetDPLinks(name)
	if len(links) > 1 {
		return "", errors.New(duplMsg)
	} else if len(links) < 1 {
		return "", errors.New(notFound)
	}
	id := functions.GetResourceID(links[0])
	return RemoveDPID(id)
}

//RemoveDPID deployment policy by ID. Returns the ID of the removed
//deployment policy and error which is != nil if the response code is different
//from 200.
func RemoveDPID(id string) (string, error) {
	link := functions.CreateResLinkForDP(id)
	url := config.URL + link
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil
}

//AddDP adds deployment policy by provided name and description.
//Returns the ID of the added deployment policy and error which is
//!= nil if the name or description strings is empty string or if the
//response code is different from 200.
func AddDP(dpName, dpDescription string) (string, error) {
	url := config.URL + "/resources/deployment-policies"
	if !checkNameDesc(dpName, dpDescription) {
		return "", errors.New("Name or description is missing.")
	}
	dp := &DeploymentPolicy{
		Name:             dpName,
		Description:      dpDescription,
		DocumentSelfLink: nil,
	}
	jsonBody, err := json.Marshal(dp)
	functions.CheckJson(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	dp = &DeploymentPolicy{}
	err = json.Unmarshal(respBody, dp)
	functions.CheckJson(err)
	return dp.GetID(), nil
}

//EditDP edits deployment policy. The parameters that functions takes are
//the name of desired deployment policy to edit, the new name and the new description.
//Pass empty string in case you want to modify some property. Returns the ID of edited
//deployment policy and error which is != nil if none or one or more deployment policies
//have the same name or if the response code is different from 200.
func EditDP(dpName, newName, newDescription string) (string, error) {
	links := GetDPLinks(dpName)
	if len(links) > 1 {
		return "", errors.New(duplMsg)
	} else if len(links) < 1 {
		return "", errors.New(notFound)
	}
	id := functions.GetResourceID(links[0])
	return EditDPID(id, newName, newDescription)
}

//EditDPID edits deployment policy by ID. The parameters that function takes are
//the ID of desired deployment policy to edit, the new name and the new description.
//Pass empty string in case you want to modify some property. Returns the ID of edited
//deployment policy and error which is != nil if the response code is different from 200.
func EditDPID(id, newName, newDescription string) (string, error) {
	url := config.URL + functions.CreateResLinkForDP(id)
	dp := &DeploymentPolicy{
		Name:             newName,
		Description:      newDescription,
		DocumentSelfLink: nil,
	}
	jsonBody, err := json.Marshal(dp)
	functions.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	dp = &DeploymentPolicy{}
	err = json.Unmarshal(respBody, dp)
	functions.CheckJson(err)
	return dp.GetID(), nil
}

//Checks if either the name or the description are empty strings.
func checkNameDesc(dpName, dpDescription string) bool {
	if dpName == "" || len(dpDescription) < 1 {
		return false
	}
	return true
}

//GetDPLinks takes deployment policy name as parameter.
//Return array of self links of deployment policies with the same name.
func GetDPLinks(name string) []string {
	dpl := DeploymentPolicyList{}
	dpl.FetchDP()
	links := make([]string, 0)
	for _, dp := range dpl.DocumentLinks {
		val := dpl.Documents[dp]
		if name == val.Name {
			links = append(links, dp)
		}
	}

	return links
}

//GetDPName takes deployment policy self link as parameter and returns it's name.
func GetDPName(link string) (string, error) {
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	dp := DeploymentPolicy{}
	//Ignoring error, because default deployment policy is crashing
	_ = json.Unmarshal(respBody, dp)
	//functions.CheckJson(err)
	return dp.Name, nil
}
