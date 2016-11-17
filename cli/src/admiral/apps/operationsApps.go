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
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"admiral/businessgroups"
	"admiral/client"
	"admiral/config"
	"admiral/containers"
	"admiral/projects"
	"admiral/templates"
	"admiral/track"
	"admiral/utils"
	"admiral/utils/selflink"
)

var (
	DuplicateNamesError      = errors.New("Duplicates found, provide the ID of the specific aplication.")
	ApplicationNotFoundError = errors.New("Application not found.")
)

//Function to start application.
//For parameter takes application name and bool to trigger or not waiting for task.
//Returns bool to specify if app is starting.
func StartApp(name string, asyncTask bool) ([]string, error) {
	resourceLinks := GetAppLinks(name)
	if len(resourceLinks) > 1 {
		return nil, DuplicateNamesError
	} else if len(resourceLinks) < 1 {
		return nil, ApplicationNotFoundError
	}

	id := utils.GetResourceID(resourceLinks[0])
	return StartAppID(id, asyncTask)
}

//Same as StartApp() but takes app's ID in order to avoid conflict from duplicate names.
func StartAppID(id string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	var (
		resLinks []string
		err      error
	)
	fullIds, err := selflink.GetFullIds([]string{id}, new(ListApps), utils.APPLICATION)
	utils.CheckIdError(err)

	resourceLinks := utils.CreateResLinksForApps(fullIds)
	oc := &containers.OperationContainer{
		Operation:     "Container.Start",
		ResourceLinks: resourceLinks,
		ResourceType:  "COMPOSITE_COMPONENT",
	}

	jsonBody, err := json.Marshal(oc)
	utils.CheckJsonError(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	if !asyncTask {
		resLinks, err = track.StartWaitingFromResponse(respBody)
		return resLinks, err
	}
	return nil, nil
}

//Function to stop application.
//For parameter takes application name and bool to trigger or not waiting for task.
//Returns bool to specify if app is stopping.
func StopApp(name string, asyncTask bool) ([]string, error) {
	resourceLinks := GetAppLinks(name)
	if len(resourceLinks) > 1 {
		return nil, DuplicateNamesError
	} else if len(resourceLinks) < 1 {
		return nil, ApplicationNotFoundError
	}
	id := utils.GetResourceID(resourceLinks[0])
	return StopAppID(id, asyncTask)
}

//Same as StopApp() but takes app's ID in order to avoid conflict from duplicate names.
func StopAppID(id string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	var (
		resLinks []string
		err      error
	)
	fullIds, err := selflink.GetFullIds([]string{id}, new(ListApps), utils.APPLICATION)
	utils.CheckIdError(err)
	resourceLinks := utils.CreateResLinksForApps(fullIds)
	oc := &containers.OperationContainer{
		Operation:     "Container.Stop",
		ResourceLinks: resourceLinks,
		ResourceType:  "COMPOSITE_COMPONENT",
	}

	jsonBody, err := json.Marshal(oc)
	utils.CheckJsonError(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	if !asyncTask {
		resLinks, err = track.StartWaitingFromResponse(respBody)
		return resLinks, err
	}
	return nil, nil
}

//Function to remove application.
//For parameter takes application name and bool to trigger or not waiting for task.
//Returns bool to specify if app is removing.
func RemoveApp(name string, asyncTask bool) ([]string, error) {
	resourceLinks := GetAppLinks(name)
	if len(resourceLinks) > 1 {
		return nil, DuplicateNamesError
	} else if len(resourceLinks) < 1 {
		return nil, ApplicationNotFoundError
	}
	id := utils.GetResourceID(resourceLinks[0])
	return RemoveAppID(id, asyncTask)
}

//Same as RemoveApp() but takes app's ID in order to avoid conflict from duplicate names.
func RemoveAppID(id string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	var (
		resLinks []string
		err      error
	)

	fullIds, err := selflink.GetFullIds([]string{id}, new(ListApps), utils.APPLICATION)
	utils.CheckIdError(err)
	resourceLinks := utils.CreateResLinksForApps(fullIds)
	oc := &containers.OperationContainer{
		Operation:     "Container.Delete",
		ResourceLinks: resourceLinks,
		ResourceType:  "COMPOSITE_COMPONENT",
	}
	jsonBody, err := json.Marshal(oc)
	utils.CheckJsonError(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	if !asyncTask {
		resLinks, err = track.StartWaitingFromResponse(respBody)
		return resLinks, err
	}
	return nil, nil
}

type CompositeDescription struct {
	DocumentSelfLink string `json:"documentSelfLink"`
}

//Function to provision application.
//For parameter takes application name and bool to trigger or not waiting for task.
//Returns bool to specify if app is provisioning.
func RunApp(app, tenantId string, asyncTask bool) ([]string, error) {
	links := queryTemplateName(app)
	if len(links) > 1 {
		return nil, templates.DuplicateNamesError
	} else if len(links) < 1 {
		return nil, templates.TemplateNotFoundError
	}

	id := utils.GetResourceID(links[0])
	return RunAppID(id, tenantId, asyncTask)
}

//Same as RunApp() but takes app's ID in order to avoid conflict from duplicate names.
func RunAppID(id, tenantId string, asyncTask bool) ([]string, error) {
	jsonBody := make(map[string]string, 0)
	fullId, err := selflink.GetFullId(id, new(templates.CompositeDescriptionList), utils.TEMPLATE)
	utils.CheckIdError(err)
	link := utils.CreateResLinkForTemplate(fullId)
	jsonBody["documentSelfLink"] = link
	reqBody, err := json.Marshal(jsonBody)
	utils.CheckJsonError(err)

	url := config.URL + "/resources/composite-descriptions-clone"
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(reqBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	cd := &CompositeDescription{}
	err = json.Unmarshal(respBody, cd)
	utils.CheckJsonError(err)

	link = cd.DocumentSelfLink
	tenantLinks := setTenantLink(tenantId)

	ra := RunApplication{
		ResourceDescriptionLink: link,
		ResourceType:            "COMPOSITE_COMPONENT",
		TenantLinks:             tenantLinks,
	}
	resLinks, err := ra.run(asyncTask)
	ids := utils.GetResourceIDs(resLinks)
	return ids, err
}

func RunAppFile(dirF, tenantId string, keepTemplate, asyncTask bool) ([]string, error) {
	id, _ := templates.Import(dirF)
	resLinks, err := RunAppID(id, tenantId, false)
	if !keepTemplate {
		templates.RemoveTemplateID(id)
	}
	ids := utils.GetResourceIDs(resLinks)
	return ids, err
}

//Function to get links of templates with name equal to passed in paramater.
func queryTemplateName(tmplName string) []string {
	tmplNameArr := strings.Split(tmplName, "/")
	name := tmplNameArr[len(tmplNameArr)-1]
	lt := templates.TemplatesList{}
	lt.FetchTemplates(name)
	links := lt.GetTemplateLinks(tmplName)
	return links

}

type RunApplication struct {
	ResourceDescriptionLink string   `json:"resourceDescriptionLink"`
	ResourceType            string   `json:"resourceType"`
	TenantLinks             []string `json:"tenantLinks"`
}

//Function that send request to the Admiral API to provision application.
func (ra *RunApplication) run(asyncTask bool) ([]string, error) {
	var (
		links []string
		err   error
	)
	url := config.URL + "/requests"
	jsonBody, err := json.Marshal(ra)
	utils.CheckJsonError(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	if !asyncTask {
		links, err = track.StartWaitingFromResponse(respBody)
		return links, err
	}
	return nil, nil
}

func setTenantLink(tenantLinkId string) []string {
	tenantLinks := make([]string, 0)
	if tenantLinkId == "" {
		return nil
	}
	if !utils.IsVraMode {
		fullProjectId, err := selflink.GetFullId(tenantLinkId, new(projects.ProjectList), utils.PROJECT)
		utils.CheckIdError(err)
		projectLink := utils.CreateResLinkForProject(fullProjectId)
		tenantLinks = append(tenantLinks, projectLink)
	} else {
		fullBusinessGroupId, err := businessgroups.GetFullId(tenantLinkId)
		utils.CheckIdError(err)
		businessGroupLink := utils.CreateResLinkForBusinessGroup(fullBusinessGroupId)
		tenantLinks = append(tenantLinks, businessGroupLink)
		tenantLinks = append(tenantLinks, "/tenants/"+utils.GetTenant())
	}
	return tenantLinks
}

func GetAppLinks(name string) []string {
	la := ListApps{}
	la.FetchApps("")
	links := make([]string, 0)
	for i := range la.DocumentLinks {
		val := la.Documents[la.DocumentLinks[i]]
		if val.Name == name {
			links = append(links, la.DocumentLinks[i])
		}
	}
	return links
}

type AppComponent struct {
	ComponentType     string   `json:"ComponentType"`
	Id                string   `json:"ID"`
	NetworksConnected []string `json:"NetworksConnected,omitempty"`
}

type InspectApp struct {
	Id         string          `json:"ID"`
	Name       string          `json:"Name"`
	Containers int             `json:"ContainersCount"`
	Networks   int             `json:"NetworksCount"`
	Components []*AppComponent `json:"Components"`
}

func InspectID(id string) (string, error) {
	fullIds, err := selflink.GetFullIds([]string{id}, new(ListApps), utils.APPLICATION)
	utils.CheckIdError(err)
	resourceLinks := utils.CreateResLinksForApps(fullIds)
	url := config.URL + resourceLinks[0]
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	app := &App{}
	err = json.Unmarshal(respBody, app)
	utils.CheckJsonError(err)

	ia := &InspectApp{
		Id:         app.GetID(),
		Name:       app.Name,
		Containers: app.GetContainersCount(),
		Networks:   app.GetNetworksCount(),
		Components: make([]*AppComponent, 0),
	}
	for i, contLink := range app.ComponentLinks {
		component := &AppComponent{}
		component.Id = utils.GetResourceID(contLink)
		if app.IsContainer(i) {
			component.ComponentType = "Container"
			c := containers.GetContainer(component.Id)
			component.NetworksConnected = utils.ValuesToStrings(utils.GetMapKeys(c.Networks))
		} else {
			component.ComponentType = "Network"
		}

		ia.Components = append(ia.Components, component)
	}
	jsonBody, _ := json.MarshalIndent(ia, "", "    ")
	return string(jsonBody), nil
}
