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

package instancetypes

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"reflect"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/endpoints"
	"admiral/hosts"
	"admiral/utils"
	"admiral/utils/urlutils"
)

type EndpointType int

func (et *EndpointType) getName() string {
	switch reflect.ValueOf(et).Elem().Int() {
	case 0:
		return "aws"
	case 1:
		return "azure"
	case 2:
		return "vsphere"
	default:
		return ""
	}
}

const (
	AWS EndpointType = iota
	AZURE
	VSPHERE
)

type EnvMapping struct {
	Name         string                                  `json:"name"`
	EndpointType string                                  `json:"endpointType"`
	Properties   map[string]map[string]map[string]string `json:"properties"`
}

func (em *EnvMapping) fetchMappings(e EndpointType) error {
	config.GetCfg()
	url := urlutils.BuildUrl(urlutils.EnvMapping, nil, true) + e.getName()
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return respErr
	}
	err := json.Unmarshal(respBody, em)
	return err
}

const (
	NotLoggedInMessage        = "Please login to see the available instance types."
	NotAvailableInstanceTypes = "Instance types are not available."
	NotAvailableImageTypes    = "Image types are not avialable"
	NotAvailableDestination   = "Destinations are not avialable"

	DestinationsFetchLinkFormat = "/resources/compute?documentType=true&$count=true&$limit=10&$orderby=creationTimeMicros+asc&$filter=descriptionLink+ne+'/resources/compute-descriptions/*-parent-compute-desc'+and+customProperties/__endpointType+ne+'*'+and+customProperties/__computeContainerHost+ne+'*'+and+customProperties/__computeType+ne+'VirtualMachine'+and+powerState+eq+'ON'+and+resourcePoolLink+eq+'%s'"
)

var (
	//map[ID]Description
	AwsInstanceTypes     map[string]string
	AzureInstanceTypes   map[string]string
	VsphereInstanceTypes map[string]string
)

func init() {
	AwsInstanceTypes = make(map[string]string)
	AzureInstanceTypes = make(map[string]string)
	VsphereInstanceTypes = make(map[string]string)

	AwsInstanceTypes["small"] = "Small (1 CPU, 1 GB Memory)"
	AwsInstanceTypes["medium"] = "Medium (1 CPU, 2 GB Memory)"
	AwsInstanceTypes["large"] = "Large (2 CPUs, 8 GB Memory)"
	AwsInstanceTypes["xlarge"] = "X-Large (4 CPU, 16 GB Memory)"

	AzureInstanceTypes["small"] = "Small (1 CPU, 0.75 GB Memory)"
	AzureInstanceTypes["medium"] = "Medium (1 CPU, 1.75 GB Memory)"
	AzureInstanceTypes["large"] = "Large (2 CPU, 3.50 GB Memory)"
	AzureInstanceTypes["xlarge"] = "X-Large (4 CPU, 14 GB Memory)"

	VsphereInstanceTypes["small"] = "Small (1 CPU, 1 GB Memory)"
	VsphereInstanceTypes["medium"] = "Medium (1 CPU, 2 GB Memory)"
	VsphereInstanceTypes["large"] = "Large (2 CPU, 8 GB Memory)"
	VsphereInstanceTypes["xlarge"] = "X-Large (4 CPU, 16 GB Memory)"

}

func GetOutputString(e EndpointType) string {
	client.SetCustomTimeout(2)
	em := &EnvMapping{}
	err := em.fetchMappings(e)

	if _, ok := err.(client.AuthorizationError); ok {
		return NotLoggedInMessage
	}
	if _, ok := em.Properties["instanceType"]; !ok {
		return NotAvailableInstanceTypes
	}

	if _, ok := em.Properties["instanceType"]["mappings"]; !ok {
		return NotAvailableInstanceTypes
	}

	if _, ok := em.Properties["imageType"]; !ok {
		return NotAvailableImageTypes
	}

	if _, ok := em.Properties["imageType"]["mappings"]; !ok {
		return NotAvailableImageTypes
	}
	upperCaseName := strings.ToUpper(e.getName())
	var buffer bytes.Buffer
	buffer.WriteString(fmt.Sprintf("%s Instance Types:\n", upperCaseName))
	buffer.WriteString("ID\tDESCRIPTION\n")
	for id := range em.Properties["instanceType"]["mappings"] {
		buffer.WriteString(utils.GetTabSeparatedString(id, AwsInstanceTypes[id]))
		buffer.WriteString("\n")
	}
	strings.TrimSpace(buffer.String())
	buffer.WriteString(fmt.Sprintf("\n%s Available OS:\n", upperCaseName))
	for id := range em.Properties["imageType"]["mappings"] {
		buffer.WriteString(id)
		buffer.WriteString("\n")
	}

	if e == VSPHERE {
		buffer.WriteString("\n")
		buffer.WriteString(getDestinationsOutputString())
	}

	client.SetCustomTimeout(config.CLIENT_TIMEOUT)
	return strings.TrimSpace(buffer.String())
}

func createDestinationFetchLink(resourcePoolLink string) string {
	if resourcePoolLink == "" {
		return ""
	}
	return fmt.Sprintf(DestinationsFetchLinkFormat, resourcePoolLink)
}

func getDestinationsOutputString() string {
	var buffer bytes.Buffer
	buffer.WriteString("\nVSPHERE Destinations\n")
	buffer.WriteString("DESTINATION ID\tADDRESS\tENDPOINT ID\tNAME\tTYPE\n")
	endpointsToRpLinks := endpoints.GetResourcePoolLinksOfVsphereEndpoints()

	for endpoint, rpLink := range endpointsToRpLinks {
		url := config.URL + createDestinationFetchLink(rpLink)
		req, _ := http.NewRequest("GET", url, nil)
		_, respBody, respErr := client.ProcessRequest(req)
		utils.CheckBlockingError(respErr)
		hl := &hosts.HostsList{}
		err := json.Unmarshal(respBody, hl)
		utils.CheckBlockingError(err)
		for _, dest := range hl.Documents {
			buffer.WriteString(utils.GetTabSeparatedString(dest.GetID(), dest.Name, endpoint.GetID(), endpoint.Name, endpoint.EndpointType))
			buffer.WriteString("\n")
		}
	}
	return strings.TrimSpace(buffer.String())
}
