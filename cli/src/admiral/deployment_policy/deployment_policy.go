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

package deployment_policy

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/common"
	"admiral/common/base_types"
	"admiral/common/utils"
	"admiral/common/utils/selflink_utils"
	"admiral/common/utils/uri_utils"
	"admiral/config"
)

var (
	DeploymentPolicyNotFoundError        = errors.New("Deployment policy not found.")
	DuplicateNamesError                  = errors.New("Duplicates with that name found. Please provide the ID for the specific deployment policy.")
	DeploymentPolicyNameNotProvidedError = errors.New("Deployment policy name not provided.")
)

type DeploymentPolicy struct {
	base_types.ServiceDocument

	Name        string `json:"name,omitempty"`
	Description string `json:"description,omitempty"`
}

//GetID returns the ID of the deployment policy as string.
func (dp *DeploymentPolicy) GetID() string {
	return strings.Replace(dp.DocumentSelfLink, "/resources/deployment-policies/", "", -1)
}

type DeploymentPolicyList struct {
	DocumentLinks []string                    `json:"documentLinks"`
	Documents     map[string]DeploymentPolicy `json:"documents"`
}

func (dpl *DeploymentPolicyList) GetCount() int {
	return len(dpl.DocumentLinks)
}

func (dpl *DeploymentPolicyList) GetResource(index int) selflink_utils.Identifiable {
	resource := dpl.Documents[dpl.DocumentLinks[index]]
	return &resource
}

func (dpl *DeploymentPolicyList) Renew() {
	*dpl = DeploymentPolicyList{}
}

//FetchDP fetches existing deployment policies and returns their count.
func (dpl *DeploymentPolicyList) FetchDP() (int, error) {
	url := uri_utils.BuildUrl(uri_utils.DeploymentPolicy, uri_utils.GetCommonQueryMap(), true)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, dpl)
	utils.CheckBlockingError(err)
	return len(dpl.DocumentLinks), nil
}

//Print prints already fetched deployment policies.
func (dpl *DeploymentPolicyList) GetOutputString() string {
	if dpl.GetCount() < 1 {
		return selflink_utils.NoElementsFoundMessage
	}
	var buffer bytes.Buffer
	buffer.WriteString("ID\tNAME\tDESCRIPTION")
	buffer.WriteString("\n")
	for _, link := range dpl.DocumentLinks {
		val := dpl.Documents[link]
		output := utils.GetTabSeparatedString(val.GetID(), val.Name, val.Description)
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

//RemoveDPID deployment policy by ID. Returns the ID of the removed
//deployment policy and error which is != nil if the response code is different
//from 200.
func RemoveDPID(id string) (string, error) {
	fullId, err := selflink_utils.GetFullId(id, new(DeploymentPolicyList), common.DEPLOYMENT_POLICY)
	utils.CheckBlockingError(err)
	link := utils.CreateResLinkForDeploymentPolicies(fullId)
	url := config.URL + link
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return fullId, nil
}

//AddDP adds deployment policy by provided name and description.
//Returns the ID of the added deployment policy and error which is
//!= nil if the name or description strings is empty string or if the
//response code is different from 200.
func AddDP(dpName, dpDescription string) (string, error) {
	url := uri_utils.BuildUrl(uri_utils.DeploymentPolicy, nil, true)
	if dpName == "" {
		return "", DeploymentPolicyNameNotProvidedError
	}
	dp := &DeploymentPolicy{
		Name:        dpName,
		Description: dpDescription,
	}
	jsonBody, err := json.Marshal(dp)
	utils.CheckBlockingError(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	dp = &DeploymentPolicy{}
	err = json.Unmarshal(respBody, dp)
	utils.CheckBlockingError(err)
	return dp.GetID(), nil
}

//EditDPID edits deployment policy by ID. The parameters that function takes are
//the ID of desired deployment policy to edit, the new name and the new description.
//Pass empty string in case you want to modify some property. Returns the ID of edited
//deployment policy and error which is != nil if the response code is different from 200.
func EditDPID(id, newName, newDescription string) (string, error) {
	fullId, err := selflink_utils.GetFullId(id, new(DeploymentPolicyList), common.DEPLOYMENT_POLICY)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForDeploymentPolicies(fullId)
	dp := &DeploymentPolicy{
		Name:        newName,
		Description: newDescription,
	}
	jsonBody, err := json.Marshal(dp)
	utils.CheckBlockingError(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	dp = &DeploymentPolicy{}
	err = json.Unmarshal(respBody, dp)
	utils.CheckBlockingError(err)
	return dp.GetID(), nil
}

func GetDeploymentPolicy(idOrName string) *DeploymentPolicy {
	fullId, err := selflink_utils.GetFullId(idOrName, new(DeploymentPolicyList), common.DEPLOYMENT_POLICY)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForDeploymentPolicies(fullId)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	utils.CheckBlockingError(respErr)
	dp := &DeploymentPolicy{}
	err = json.Unmarshal(respBody, dp)
	utils.CheckBlockingError(err)
	return dp
}
