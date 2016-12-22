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

package hosts

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/credentials"
	"admiral/endpoints"
	"admiral/properties"
	"admiral/tags"
	"admiral/track"
	"admiral/utils"
	"admiral/utils/selflink"
	"admiral/utils/urlutils"
)

var (
	InvalidInstanceTypeError = errors.New("Invalid instance type.")
)

type EndpointType int

const (
	AWS EndpointType = iota
	AZURE
	VSPHERE
	GCP
)

type ComputeDescription struct {
	AuthCredentialsLink string             `json:"authCredentialsLink,omitempty"`
	Name                string             `json:"name,omitempty"`
	InstanceType        string             `json:"instanceType,omitempty"`
	SupportedChildren   []string           `json:"supportedChildren,omitempty"`
	TagLinks            []string           `json:"tagLinks,omitempty"`
	CustomProperties    map[string]*string `json:"customProperties,omitempty"`
	DocumentSelfLink    string             `json:"documentSelfLink,omitempty"`
}

func (cd *ComputeDescription) SetAuthCredentialsLink(id string) error {
	if id == "" {
		return nil
	}
	fullId, err := selflink.GetFullId(id, new(credentials.CredentialsList), utils.CREDENTIALS)
	if err != nil {
		return err
	}
	cd.AuthCredentialsLink = utils.CreateResLinkForCredentials(fullId)
	return nil
}

func (cd *ComputeDescription) SetSupportedChildren() {
	if cd.SupportedChildren == nil {
		cd.SupportedChildren = make([]string, 0)
	}
	cd.SupportedChildren = append(cd.SupportedChildren, "DOCKER_CONTAINER")
}

func (cd *ComputeDescription) SetOsImage(osImage string) error {
	osImage = strings.ToLower(osImage)
	cd.CustomProperties["imageType"] = &osImage
	return nil
}

func (cd *ComputeDescription) SetDestination(destinationId string) {
	destLink := utils.CreateResLinksForHosts(destinationId)
	cd.CustomProperties["__placementLink"] = &destLink
}

func (cd *ComputeDescription) SetEndpoint(endpointId string) error {
	fullId, err := selflink.GetFullId(endpointId, new(endpoints.EndpointList), utils.ENDPOINT)
	if err != nil {
		return err
	}
	link := utils.CreateResLinkForEndpoint(fullId)
	cd.CustomProperties["__endpointLink"] = &link
	return nil
}

func (cd *ComputeDescription) SetCustomProperties(customProps []string) {
	if cd.CustomProperties == nil {
		cd.CustomProperties = make(map[string]*string, 0)
	}
	properties.ParseCustomProperties(customProps, cd.CustomProperties)
}

func (cd *ComputeDescription) SetInstanceType(instanceType string) error {
	if instanceType == "" {
		return InvalidInstanceTypeError
	}
	cd.InstanceType = instanceType
	return nil
}

func (cd *ComputeDescription) AddTags(tagsInput []string) error {
	if cd.TagLinks == nil {
		cd.TagLinks = make([]string, 0)
	}
	for _, ti := range tagsInput {
		tagId, err := tags.GetTagIdByEqualKeyVals(ti, true)
		if err != nil {
			return err
		}
		tagLink := utils.CreateResLinkForTag(tagId)
		if tagLink != "" && !cd.containsTagLink(tagLink) {
			cd.TagLinks = append(cd.TagLinks, tagLink)
		}
	}
	return nil
}

func (cd *ComputeDescription) containsTagLink(tagLink string) bool {
	for _, tl := range cd.TagLinks {
		if tl == tagLink {
			return true
		}
	}
	return false
}

func NewComputeDescription(name, endpointId, instanceType, hostOS, credentialsId string,
	tagsInput, customProps []string) (*ComputeDescription, error) {
	computeDescription := &ComputeDescription{}
	computeDescription.CustomProperties = make(map[string]*string, 0)
	computeDescription.Name = name
	computeDescription.SetSupportedChildren()
	computeDescription.SetCustomProperties(customProps)

	err := computeDescription.AddTags(tagsInput)
	if err != nil {
		return nil, err
	}
	err = computeDescription.SetOsImage(hostOS)
	if err != nil {
		return nil, err
	}
	err = computeDescription.SetEndpoint(endpointId)
	if err != nil {
		return nil, err
	}
	err = computeDescription.SetInstanceType(instanceType)
	if err != nil {
		return nil, err
	}
	err = computeDescription.SetAuthCredentialsLink(credentialsId)
	if err != nil {
		return nil, err
	}

	return computeDescription, nil
}

func CreateHostAws(name, endpointId, instanceType, hostOS, credentialsId string, clusterSize int,
	tagsInput, customProps []string, asyncTask bool) (string, error) {

	computeDescription, err := NewComputeDescription(name, endpointId, instanceType, hostOS, credentialsId, tagsInput, customProps)

	if err != nil {
		return "", err
	}

	return processComputeDescription(computeDescription, clusterSize, asyncTask)
}

func CreateHostAzure(name, endpointId, instanceType, hostOS, credentialsId string, clusterSize int,
	tagsInput, customProps []string, asyncTask bool) (string, error) {

	computeDescription, err := NewComputeDescription(name, endpointId, instanceType, hostOS, credentialsId, tagsInput, customProps)

	if err != nil {
		return "", err
	}

	return processComputeDescription(computeDescription, clusterSize, asyncTask)
}

func CreateHostVsphere(name, endpointId, instanceType, hostOS, destinationId, credentialsId string, clusterSize int,
	tagsInput, customProps []string, asyncTask bool) (string, error) {

	computeDescription, err := NewComputeDescription(name, endpointId, instanceType, hostOS, credentialsId, tagsInput, customProps)
	if err != nil {
		return "", err
	}
	computeDescription.SetDestination(destinationId)
	return processComputeDescription(computeDescription, clusterSize, asyncTask)
}

type ProvisionHostOperation struct {
	Operation               string `json:"operation"`
	ResourceCount           int    `json:"resourceCount"`
	ResourceDescriptionLink string `json:"resourceDescriptionLink"`
	ResourceType            string `json:"resourceType"`
}

func newProvisionHostOperation(clusterSize int, descriptionLink string) *ProvisionHostOperation {
	pho := &ProvisionHostOperation{
		Operation:               "PROVISION_CONTAINER_HOSTS",
		ResourceCount:           clusterSize,
		ResourceDescriptionLink: descriptionLink,
		ResourceType:            "CONTAINER_HOST",
	}
	return pho
}

func processComputeDescription(cd *ComputeDescription, clusterSize int, asyncTask bool) (string, error) {
	jsonBody, err := json.Marshal(cd)
	utils.CheckBlockingError(err)

	url := urlutils.BuildUrl(urlutils.ComputeDescription, nil, true)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}

	computeDescFromResp := &ComputeDescription{}
	err = json.Unmarshal(respBody, computeDescFromResp)
	utils.CheckBlockingError(err)

	pho := newProvisionHostOperation(clusterSize, computeDescFromResp.DocumentSelfLink)
	jsonBody, err = json.Marshal(pho)
	utils.CheckBlockingError(err)

	url = urlutils.BuildUrl(urlutils.RequestBrokerService, nil, true)
	req, _ = http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr = client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}

	if asyncTask {
		track.PrintTaskIdFromResponseBody(respBody)
		return "", nil
	}
	ids, err := track.StartWaitingFromResponseBody(respBody)
	return strings.Join(ids, " "), err
}
