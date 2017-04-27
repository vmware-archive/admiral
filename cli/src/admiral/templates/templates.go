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

package templates

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"sort"
	"strings"

	"admiral/client"
	"admiral/common"
	"admiral/common/base_types"
	"admiral/common/utils"
	"admiral/common/utils/selflink_utils"
	"admiral/common/utils/uri_utils"
	"admiral/config"
	"admiral/containers"
)

var (
	DuplicateNamesError   = errors.New("Templates with duplicate name found, use ID to remove the desired one.")
	TemplateNotFoundError = errors.New("Template not found.")
)

const (
	Container_Description = "Container Description"
	Network_Description   = "Network Description"
	Closure_Description   = "Closure Description"
)

type LightContainer struct {
	Name  string `json:"name"`
	Image string `json:"image"`
}

func (lc *LightContainer) GetOutput(link string) (string, error) {
	req, _ := http.NewRequest("GET", link, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	err := json.Unmarshal(respBody, lc)
	utils.CheckBlockingError(err)
	return fmt.Sprintf("   Container Name: %-22s\tContainer Image: %s\n", lc.Name, lc.Image), nil
}

type CompositeDescriptionList struct {
	DocumentLinks []string            `json:"documentLinks"`
	Documents     map[string]Template `json:"documents"`
}

func (cdl *CompositeDescriptionList) GetCount() int {
	return len(cdl.DocumentLinks)
}

func (cdl *CompositeDescriptionList) GetResource(index int) selflink_utils.Identifiable {
	resource := cdl.Documents[cdl.DocumentLinks[index]]
	return &resource
}

func (cdl *CompositeDescriptionList) Renew() {
	*cdl = CompositeDescriptionList{}
}

type TemplateSorter []Template

func (ts TemplateSorter) Len() int           { return len(ts) }
func (ts TemplateSorter) Swap(i, j int)      { ts[i], ts[j] = ts[j], ts[i] }
func (ts TemplateSorter) Less(i, j int) bool { return ts[i].Name < ts[j].Name }

type Template struct {
	base_types.ServiceDocument

	Name                  string   `json:"name"`
	TemplateType          string   `json:"templateType"`
	DescriptionLinks      []string `json:"descriptionLinks"`
	IsAutomated           bool     `json:"is_automated"`
	IsOfficial            bool     `json:"is_official"`
	StarsCount            int32    `json:"star_count"`
	Description           string   `json:"description"`
	ParentDescriptionLink string   `json:"parentDescriptionLink"`
}

func (t *Template) GetContainersCount() int {
	count := 0
	for _, link := range t.DescriptionLinks {
		if strings.Contains(link, "/container-descriptions/") {
			count++
		}
	}
	return count
}

func (t *Template) GetNetworksCount() int {
	count := 0
	for _, link := range t.DescriptionLinks {
		if strings.Contains(link, uri_utils.NetworkDescription.GetBaseUrl()) {
			count++
		}
	}
	return count
}

func (t *Template) GetClosuresCount() int {
	count := 0
	for _, link := range t.DescriptionLinks {
		if strings.Contains(link, uri_utils.ClosureDescription.GetBaseUrl()) {
			count++
		}
	}
	return count
}

//GetID returns the ID of the template.
func (t *Template) GetID() string {
	return utils.GetResourceID(t.DocumentSelfLink)
}

func (t *Template) IsContainer(index int) bool {
	link := t.DescriptionLinks[index]
	if strings.Contains(link, uri_utils.ContainerDescription.GetBaseUrl()) {
		return true
	}
	return false
}

func (t *Template) GetResourceType(index int) common.ResourceType {
	link := t.DescriptionLinks[index]
	if strings.Contains(link, uri_utils.ContainerDescription.GetBaseUrl()) {
		return common.CONTAINER
	} else if strings.Contains(link, uri_utils.NetworkDescription.GetBaseUrl()) {
		return common.NETWORK
	} else if strings.Contains(link, uri_utils.ClosureDescription.GetBaseUrl()) {
		return common.CLOSURE
	} else {
		return -1
	}
}

type TemplatesList struct {
	Results []Template `json:"results"`
}

func (tl *TemplatesList) GetCount() int {
	return len(tl.Results)
}

func (tl *TemplatesList) GetResource(index int) selflink_utils.Identifiable {
	resource := tl.Results[0]
	return &resource
}

func (tl *TemplatesList) Renew() {
	*tl = TemplatesList{}
}

//FetchTemplates fetches the templates by query. If it's needed to
//fetch all templates, empty string should be passed. Returns the
//count of fetched templates.
func (lt *TemplatesList) FetchTemplates(queryF string) (int, error) {
	url := config.URL + "/templates?documentType=true&templatesOnly=true"
	var query string
	if queryF != "" {
		query = fmt.Sprintf("&q=%s", queryF)
		url = url + query
	} else {
		query = "&q=*"
		url = url + query
	}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, lt)
	utils.CheckBlockingError(err)
	return len(lt.Results), nil
}

//PrintWithoutContainers prints already fetched templates without
//printing containers inside the templates.
func (lt *TemplatesList) GetOutputStringWithoutContainers() string {
	var buffer bytes.Buffer
	if len(lt.Results) < 1 {
		return selflink_utils.NoElementsFoundMessage
	}
	sort.Sort(TemplateSorter(lt.Results))
	buffer.WriteString("ID\tNAME\tCONTAINERS\tNETWORKS\tCLOSURES\n")
	for _, template := range lt.Results {
		if template.ParentDescriptionLink != "" {
			continue
		}
		output := utils.GetTabSeparatedString(template.GetID(), template.Name,
			template.GetContainersCount(), template.GetNetworksCount(), template.GetClosuresCount())
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

//PrintWithContainer prints already fetched template with containers inside them.
func (lt *TemplatesList) GetOutputStringWithContainers() (string, error) {
	//TODO: Figure out some better for formatting for better vision on console.
	url := config.URL
	var buffer bytes.Buffer
	if len(lt.Results) < 1 {
		return selflink_utils.NoElementsFoundMessage, nil
	}
	buffer.WriteString("ID\tNAME\tCONTAINERS\tNETWORKS\tCLOSURES\n")
	for _, template := range lt.Results {
		if template.ParentDescriptionLink != "" {
			continue
		}
		output := utils.GetTabSeparatedString(template.GetID(), template.Name,
			template.GetContainersCount(), template.GetNetworksCount(), template.GetClosuresCount())
		buffer.WriteString(output)
		buffer.WriteString("\n")
		for _, link := range template.DescriptionLinks {
			currentUrl := url + link
			container := &LightContainer{}
			output, err := container.GetOutput(currentUrl)
			if err != nil {
				return "", err
			}
			buffer.WriteString(output)
			buffer.WriteString("\n")
		}
	}
	return strings.TrimSpace(buffer.String()), nil
}

//GetTemplateLinks returns array of self links of templates which names
//match the one passed as parameter.
func (lt *TemplatesList) GetTemplateLinks(tmplName string) []string {
	links := make([]string, 0)
	for _, tmpl := range lt.Results {
		if tmplName == tmpl.Name {
			var selfLink string
			if tmpl.TemplateType != "CONTAINER_IMAGE_DESCRIPTION" {
				selfLink = tmpl.DocumentSelfLink
				links = append(links, selfLink)
			}
		}
	}
	return links
}

//RemoveTemplateID removes template by ID passed as parameter.
//Returns the ID of the removed template and error = nil or
//ID = empty string and error != nil.
func RemoveTemplateID(id string) (string, error) {
	fullId, err := selflink_utils.GetFullId(id, new(CompositeDescriptionList), common.TEMPLATE)
	utils.CheckBlockingError(err)
	link := utils.CreateResLinkForTemplate(fullId)
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Add("Pragma", "xn-force-index-update")
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	template := &Template{}
	err = json.Unmarshal(respBody, template)
	utils.CheckBlockingError(err)
	for i := range template.DescriptionLinks {
		tempLink := config.URL + template.DescriptionLinks[i]
		req, _ := http.NewRequest("DELETE", tempLink, nil)
		client.ProcessRequest(req)
	}
	req, _ = http.NewRequest("DELETE", url, nil)
	_, _, respErr = client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return fullId, nil

}

func Import(dirF string) (string, error) {
	importFile, err := ioutil.ReadFile(dirF)

	if err != nil {
		return "", err
	}

	url := config.URL + "/resources/composite-templates"

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(importFile))
	req.Header.Set("Content-Type", "application/yaml")
	resp, _, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return "", respErr
	}

	link := resp.Header.Get("Location")
	id := utils.GetResourceID(link)
	return id, nil
}

func Export(id, dirF, format string) (string, error) {
	file, err := verifyFile(dirF)
	if err != nil {
		return "", err
	}
	fullId, err := selflink_utils.GetFullId(id, new(CompositeDescriptionList), common.TEMPLATE)
	utils.CheckBlockingError(err)
	url := config.URL + "/resources/composite-templates?selfLink=" + fullId
	if format == "docker" {
		url = url + "&format=docker"
	}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		os.Remove(dirF)
		return "", err
	}
	_, err = file.Write(respBody)

	if err != nil {
		os.Remove(dirF)
		return "", err
	}
	return fullId, nil
}

func ImportKubernetes(dirF string) (string, error) {
	importFile, err := ioutil.ReadFile(dirF)

	if err != nil {
		return "", err
	}

	url := config.URL + "/resources/kubernetes-templates"

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(importFile))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}

	return string(respBody), nil
}

//Function to verify if file can be created.
//Returns the file and result of verification
func verifyFile(dirF string) (*os.File, error) {
	file, err := os.Create(dirF)
	return file, err
}

type InspectTemplate struct {
	Id         string               `json:"ID"`
	Name       string               `json:"Name"`
	Containers int                  `json:"ContainersCount"`
	Networks   int                  `json:"NetworksCount"`
	Components []*TemplateComponent `json:"Components"`
}

type TemplateComponent struct {
	ComponentType     string   `json:"ComponentType"`
	Id                string   `json:"ID"`
	Name              string   `json:"Name,omitempty"`
	Image             string   `json:"Image,omitempty"`
	NetworksConnected []string `json:"NetworksConnected,omitempty"`

	ClosureRuntime string `json:"Runtime,omitempty"`
}

func InspectID(id string) (string, error) {
	fullId, err := selflink_utils.GetFullId(id, new(CompositeDescriptionList), common.TEMPLATE)
	utils.CheckBlockingError(err)
	link := utils.CreateResLinkForTemplate(fullId)
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	template := &Template{}
	err = json.Unmarshal(respBody, template)
	utils.CheckBlockingError(err)
	it := &InspectTemplate{
		Id:         template.GetID(),
		Name:       template.Name,
		Containers: template.GetContainersCount(),
		Networks:   template.GetNetworksCount(),
		Components: make([]*TemplateComponent, 0),
	}
	for i, descLink := range template.DescriptionLinks {
		component := &TemplateComponent{}
		component.Id = utils.GetResourceID(descLink)
		switch template.GetResourceType(i) {
		case common.CONTAINER:
			component.ComponentType = Container_Description
			cd := containers.GetContainerDescription(component.Id)
			component.NetworksConnected = utils.ValuesToStrings(utils.GetMapKeys(cd.Networks))
			component.Image = cd.Image.Value
		case common.NETWORK:
			component.ComponentType = Network_Description
			component.Name = GetNetworkDescriptionName(descLink)
		case common.CLOSURE:
			closureDescription := GetClosureDescription(component.Id)
			component.ComponentType = Closure_Description
			component.Name = closureDescription.Name
			component.ClosureRuntime = closureDescription.Runtime
		}
		it.Components = append(it.Components, component)
	}
	jsonBody, _ := json.MarshalIndent(it, "", "    ")
	return string(jsonBody), nil
}

func GetNetworkDescriptionName(link string) string {
	if !strings.Contains(link, uri_utils.NetworkDescription.GetBaseUrl()) {
		link = uri_utils.NetworkDescription.GetBaseUrl() + link
	}
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	utils.CheckResponse(respErr, url)
	v := make(map[string]interface{}, 0)
	err := json.Unmarshal(respBody, &v)
	utils.CheckBlockingError(err)
	return v["name"].(string)
}
