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

package functions

import (
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
func CreateResLinksForHosts(address string) string {
	return "/resources/compute/" + address
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
//This link will be used to execute command, avoiding duplicating names.
func CreateResLinkForCerts(id string) string {
	return "/config/trust-certs/" + id
}

//Function to create resource links from the provided ID as parameter.
//This link will be used to execute command, avoiding duplicating names.
func CreateResLinkForCredentials(id string) string {
	return "/core/auth/credentials/" + id
}

//Function to create resource links from the provided ID as parameter.
//This link will be used to execute command, avoiding duplicating names.
func CreateResLinkForDP(id string) string {
	return "/resources/deployment-policies/" + id
}

//Function to create resource links from the provided ID as parameter.
//This link will be used to execute command, avoiding duplicating names.
func CreateResLinkForRP(id string) string {
	return "/resources/pools/" + id
}

//Function to create resource links from the provided ID as parameter.
//This link will be used to execute command, avoiding duplicating names.
func CreateResLinkForRegistry(id string) string {
	return "/config/registries/" + id
}

//Function to create resource links from the provided ID as parameter.
//This link will be used to execute command, avoiding duplicating names.
func CreateResLinkForPolicy(id string) string {
	return "/resources/group-policies/" + id
}

//Function to create resource links from the provided ID as parameter.
//This link will be used to execute command, avoiding duplicating names.
func CreateResLinkForGroup(id string) string {
	return "/resources/groups/" + id
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

//Function to clear part of the url in order to get the name only.
func ClearResLinksForEvents(links string) string {
	linksArr := strings.Split(links, " ")
	var link string
	if len(linksArr) > 0 {
		link = linksArr[0]
	} else {
		link = ""
	}

	if strings.Contains(link, "/resources/containers/") {
		newLink := strings.Replace(link, "/resources/containers/", "", 1)
		return newLink
	}
	newLink := strings.Replace(link, "/resources/composite-components/", "", 1)
	return newLink

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
