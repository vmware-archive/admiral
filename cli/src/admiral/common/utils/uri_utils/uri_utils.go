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

package uri_utils

import (
	"bytes"
	"fmt"
	"strings"

	"admiral/config"
)

type ResourceUri int

func (rt ResourceUri) GetBaseUrl() string {
	switch rt {
	case Project:
		return "/resources/groups"
	case CompositeComponent:
		return "/resources/composite-components"
	case RequestBrokerService:
		return "/requests"
	case Certificate:
		return "/config/trust-certs"
	case Closure:
		return "/resources/closures"
	case ClosureDescription:
		return "/resources/closure-descriptions"
	case Container:
		return "/resources/containers"
	case ContainerDescription:
		return "/resources/container-descriptions"
	case Credentials:
		return "/core/auth/credentials"
	case DeploymentPolicy:
		return "/resources/deployment-policies"
	case Environments:
		return "/config/environments/"
	case EnvironmentsAws:
		return "/config/environments/aws"
	case EnvironmentsAzure:
		return "/config/environments/azure"
	case EnvironmentsVsphere:
		return "/config/environments/vsphere"
	case Endpoint:
		return "/config/endpoints"
	case Events:
		return "/resources/event-logs"
	case Compute:
		return "/resources/compute"
	case Host:
		return "/resources/hosts"
	case ComputeDescription:
		return "/resources/compute-descriptions"
	case Image:
		return "/templates"
	case PopularImages:
		return "/popular-images"
	case ContainerLogs:
		return "/resources/container-logs"
	case Network:
		return "/resources/container-networks"
	case NetworkDescription:
		return "/resources/container-network-descriptions"
	case Placement:
		return "/resources/group-placements"
	case ElasticPlacementZone:
		return "/resources/elastic-placement-zones-config"
	case Registry:
		return "/config/registries"
	case LoginOut:
		return "/core/authn/basic"
	case TagAssignment:
		return "/resources/tag-assignment"
	default:
		return ""
	}
}

const (
	Project ResourceUri = iota
	CompositeComponent
	RequestBrokerService
	Certificate
	Closure
	Container
	ContainerDescription
	ComputeDescription
	Credentials
	DeploymentPolicy
	Environments
	EnvironmentsAws
	EnvironmentsAzure
	EnvironmentsVsphere
	Endpoint
	Events
	Compute
	Host
	Image
	PopularImages
	ContainerLogs
	Network
	NetworkDescription
	Placement
	ElasticPlacementZone
	Registry
	ClosureDescription
	LoginOut
	TagAssignment
)

func BuildUrl(resType ResourceUri, queryParameters map[string]interface{}, includeAdmiralUrl bool) string {
	var url string
	if includeAdmiralUrl {
		url = config.URL + resType.GetBaseUrl()
	} else {
		url = resType.GetBaseUrl()
	}
	return buildUrl(url, queryParameters)
}

func buildUrl(url string, queryParameters map[string]interface{}) string {
	if queryParameters == nil || len(queryParameters) == 0 {
		return url
	}
	var query bytes.Buffer
	query.WriteString(url)
	query.WriteString("?")
	for key, val := range queryParameters {
		query.WriteString(key)
		query.WriteString("=")
		query.WriteString(fmt.Sprintf("%v&", val))
	}
	return removeTrailingSeparator(query.String())
}

func removeTrailingSeparator(url string) string {
	newUrl := []rune(url)
	if strings.HasSuffix(url, "&") {
		newUrl = newUrl[0 : len(newUrl)-1]
	}
	return string(newUrl)

}

func GetCommonQueryMap() map[string]interface{} {
	cqm := make(map[string]interface{})
	cqm["documentType"] = true
	cqm["expand"] = true
	cqm["$limit"] = 10000
	return cqm
}
