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

package utils

import (
	"admiral/common"
	"fmt"
	"strings"
)

//Function to create links for the given IDs passed as parameters.
//Return slice of already created links.
func CreateResLinksForContainer(args []string) []string {
	link := "/resources/containers/"
	size := len(args)
	var links = make([]string, size)
	for i := range args {
		links[i] = link + args[i]
	}
	return links
}

//Function to create links for the given IDs passed as parameters.
//Return slice of already created links.
func CreateResLinksForHosts(id string) string {
	if id == "" {
		return ""
	}
	return "/resources/compute/" + id
}

//Function to create links for the given IDs passed as parameters.
//Return slice of already created links.
func CreateResLinksForApps(args []string) []string {
	link := "/resources/composite-components/"
	size := len(args)
	var links = make([]string, size)
	for i := range args {
		links[i] = link + args[i]
	}
	return links
}

//Function to create resource links from the provided ID as parameter.
func CreateResLinkForCerts(id string) string {
	if id == "" {
		return ""
	}
	return "/config/trust-certs/" + id
}

//Function to create resource links from the provided ID as parameter.
func CreateResLinkForCredentials(id string) string {
	if id == "" {
		return ""
	}
	return "/core/auth/credentials/" + id
}

//Function to create resource links from the provided ID as parameter.
func CreateResLinkForDeploymentPolicies(id string) string {
	if id == "" {
		return ""
	}
	return "/resources/deployment-policies/" + id
}

//Function to create resource links from the provided ID as parameter.
func CreateResLinkForResourcePool(id string) string {
	if id == "" {
		return ""
	}
	return "/resources/pools/" + id
}

//Function to create resource links from the provided ID as parameter.
func CreateResLinkForRegistry(id string) string {
	if id == "" {
		return ""
	}
	return "/config/registries/" + id
}

//Function to create resource links from the provided ID as parameter.
func CreateResLinksForPlacement(id string) string {
	if id == "" {
		return ""
	}
	return "/resources/group-placements/" + id
}

//Function to create resource links from the provided ID as parameter.
func CreateResLinkForProject(id string) string {
	if id == "" {
		return ""
	}
	return "/resources/groups/" + id
}

//Function to create resource links from the provided ID as parameter.
func CreateResLinkForRequest(id string) string {
	if id == "" {
		return ""
	}
	return "/request-status/" + id
}

//Function to create resource links from the provided ID as parameter.
func CreateResLinkForTemplate(id string) string {
	if id == "" {
		return ""
	}
	return "/resources/composite-descriptions/" + id
}

//Function to create resource links from the provided ID as parameter.
func CreateResLinkForBusinessGroup(id, tenant string) string {
	if id == "" {
		return ""
	}
	return fmt.Sprintf("/tenants/%s/groups/%s", tenant, id)
}

func CreateResLinkForTenant(tenant string) string {
	if tenant == "" {
		return ""
	}
	return fmt.Sprintf("/tenants/%s", tenant)
}

func CreateResLinkForContainerDescription(id string) string {
	if id == "" {
		return ""
	}
	return "/resources/container-descriptions/" + id
}

func CreateResLinkForTag(id string) string {
	if id == "" {
		return ""
	}
	return "/resources/tags/" + id
}

func CreateResLinkForPlacementZone(id string) string {
	if id == "" {
		return ""
	}
	if strings.HasPrefix(id, "/") {
		return "/resources/elastic-placement-zones-config" + id
	}
	return "/resources/elastic-placement-zones-config/" + id
}

func CreateResLinkForEndpoint(id string) string {
	if id == "" {
		return ""
	}
	return "/resources/endpoints/" + id
}

func CreateResLinkForClosure(id string) string {
	if id == "" {
		return ""
	}
	return "/resources/closures/" + id
}

func CreateResLinkForClosureDescription(id string) string {
	if id == "" {
		return ""
	}
	return "/resources/closure-descriptions/" + id
}

//Function to create resource links from the provided ID as parameter.
func CreateResLinksForNetwork(ids []string) []string {
	links := make([]string, 0)
	for i := range ids {
		link := "/resources/container-networks/" + ids[i]
		links = append(links, link)
	}
	return links
}

//Function to change specific part of the url.
func UpdateResLinksForApps(args []string) []string {
	size := len(args)
	var links = make([]string, size)
	for _, link := range args {
		newLink := strings.Replace(link, "/resources/composite-descriptions/", "/resources/composite-components/", 1)
		links = append(links, newLink)
	}
	return links
}

//Function that extract all the links which are contained as values in the given map as argument.
//Return slice of the extracted links, which will be used to make request for either some start, stop or remove command.
func GetAllLinks(m map[string][]string) []string {
	links := make([]string, 0)

	for _, val := range m {
		for _, name := range val {
			links = append(links, name)
		}
	}
	return links
}

//GetResourceID takes any document self link and returns the last part of it
//containing the ID.
func GetResourceID(s string) string {
	arr := strings.Split(s, "/")
	return arr[len(arr)-1]
}

//Same as GetResourceID but for array of self links and returns array of IDs.
func GetResourceIDs(s []string) []string {
	ids := make([]string, 0)
	for i := range s {
		idArr := strings.Split(s[i], "/")
		id := idArr[len(idArr)-1]
		ids = append(ids, id)
	}
	return ids
}

// GetIdFilterUrl returns string which is URL required to check if the initially
// provided ID for some operation is unique and try to retrieve the full ID,
// assuming the user has used short ID.
func GetIdFilterUrl(shortId string, restype common.ResourceType) string {
	var url string
	switch restype {
	case common.APPLICATION:
		shortSelfLink := CreateResLinksForApps([]string{shortId})
		url = common.ApplicationFilterID + createIdFilter(shortSelfLink[0])
	case common.CERTIFICATE:
		shortSelfLink := CreateResLinkForCerts(shortId)
		url = common.CertFilterID + createIdFilter(shortSelfLink)
	case common.CONTAINER:
		shortSelfLink := CreateResLinksForContainer([]string{shortId})
		url = common.ContainerFilterID + createIdFilter(shortSelfLink[0])
	case common.CREDENTIALS:
		shortSelfLink := CreateResLinkForCredentials(shortId)
		url = common.CredentialsFilterID + createIdFilter(shortSelfLink)
	case common.DEPLOYMENT_POLICY:
		shortSelfLink := CreateResLinkForDeploymentPolicies(shortId)
		url = common.DeploymentPolicyFilterID + createIdFilter(shortSelfLink)
	case common.HOST:
		shortSelfLink := CreateResLinksForHosts(shortId)
		url = common.HostFilterID + createIdFilter(shortSelfLink)
	case common.NETWORK:
		shortSelfLink := CreateResLinksForNetwork([]string{shortId})
		url = common.NetworkFilterID + createIdFilter(shortSelfLink[0])
	case common.PLACEMENT:
		shortSelfLink := CreateResLinksForPlacement(shortId)
		url = common.PlacementFilterID + createIdFilter(shortSelfLink)
	case common.PLACEMENT_ZONE:
		shortSelfLink := CreateResLinkForResourcePool(shortId)
		url = common.PlacementZoneFilterID + createIdFilter(shortSelfLink)
	case common.PROJECT:
		shortSelfLink := CreateResLinkForProject(shortId)
		url = common.ProjectFilterID + createIdFilter(shortSelfLink)
	case common.REGISTRY:
		shortSelfLink := CreateResLinkForRegistry(shortId)
		url = common.RegistryFilterID + createIdFilter(shortSelfLink)
	case common.REQUEST:
		shortSelfLink := CreateResLinkForRequest(shortId)
		url = common.RequestFilterID + createIdFilter(shortSelfLink)
	case common.TEMPLATE:
		shortSelfLink := CreateResLinkForTemplate(shortId)
		url = common.TemplateFilterID + createIdFilter(shortSelfLink)
	case common.ENDPOINT:
		shortSelfLink := CreateResLinkForEndpoint(shortId)
		url = common.EndpointFilterID + createIdFilter(shortSelfLink)
	case common.CLOSURE:
		shortSelfLink := CreateResLinkForClosure(shortId)
		url = common.ClosureFilterID + createIdFilter(shortSelfLink)
	case common.CLOSURE_DESCRIPTION:
		shortSelfLink := CreateResLinkForClosureDescription(shortId)
		url = common.ClosureDescriptionFilterID + createIdFilter(shortSelfLink)
	}
	return url
}

func GetNameFilterUrl(name string, restype common.ResourceType) string {
	var url string
	switch restype {
	case common.APPLICATION:
		url = common.ApplicationFilterName + createNameFilter(name)
	case common.CERTIFICATE:
		url = common.CertFilterName + createNameFilter(name)
	case common.CLOSURE:
		url = common.ClosureFilterName + createNameFilter(name)
	case common.CLOSURE_DESCRIPTION:
		url = common.ClosureDescriptionFilterName + createNameFilter(name)
	case common.CONTAINER:
		url = common.ContainerFilterName + createNameFilter(name)
	case common.CREDENTIALS:
		url = common.CredentialsFilterName + createNameFilter(name)
	case common.DEPLOYMENT_POLICY:
		url = common.DeploymentPolicyFilterName + createNameFilter(name)
	case common.ENDPOINT:
		url = common.EndpointFilterName + createNameFilter(name)
	case common.HOST:
		url = createNameFilterHost(name)
	case common.NETWORK:
		url = common.NetworkFilterName + createNameFilter(name)
	case common.PLACEMENT:
		url = common.PlacementFilterName + createNameFilter(name)
	case common.PLACEMENT_ZONE:
		url = common.PlacementZoneFilterName + createNameFilter(name)
	case common.PROJECT:
		url = common.ProjectFilterName + createNameFilter(name)
	case common.REGISTRY:
		url = common.RegistryFilterName + createNameFilter(name)
	case common.TEMPLATE:
		url = common.TemplateFilterName + createNameFilter(name)

	}
	return url
}

// Help function to build up the string containing the URL.
func createIdFilter(shortSelfLink string) string {
	return fmt.Sprintf("'%s*'", shortSelfLink)
}

func createNameFilter(name string) string {
	return fmt.Sprintf("'%s'", name)
}

func createNameFilterHost(name string) string {
	return fmt.Sprintf(common.HostFilterName, name, name, name)
}
