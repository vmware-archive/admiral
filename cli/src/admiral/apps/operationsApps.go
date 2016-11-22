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

// StartApp starts application by specified name. It will return error
// in case the name is non-unique. Currently not being used because
// starting application by name is disabled. As second parameter takes
// boolean to specify if waiting for this task is needed.
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

// StartAppID starts application by it's ID. As second parameter takes
// boolean to specify if waiting for this task is needed.
// Usage of short unique IDs is supported for this operation.
func StartAppID(id string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	var (
		resLinks []string
		err      error
	)
	fullIds, err := selflink.GetFullIds([]string{id}, new(ListApps), utils.APPLICATION)
	utils.CheckBlockingError(err)

	resourceLinks := utils.CreateResLinksForApps(fullIds)
	oc := &containers.OperationContainer{
		Operation:     "Container.Start",
		ResourceLinks: resourceLinks,
		ResourceType:  "COMPOSITE_COMPONENT",
	}

	jsonBody, err := json.Marshal(oc)
	utils.CheckBlockingError(err)
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

// StopApp stops application by specified name. It will return error
// in case the name is non-unique. Currently not being used because
// stopping application by name is disabled. As second parameter takes
// boolean to specify if waiting for this task is needed.
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

// StopAppID stops application by it's ID. As second parameter takes
// boolean to specify if waiting for this task is needed.
// Usage of short unique IDs is supported for this operation.
func StopAppID(id string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	var (
		resLinks []string
		err      error
	)
	fullIds, err := selflink.GetFullIds([]string{id}, new(ListApps), utils.APPLICATION)
	utils.CheckBlockingError(err)
	resourceLinks := utils.CreateResLinksForApps(fullIds)
	oc := &containers.OperationContainer{
		Operation:     "Container.Stop",
		ResourceLinks: resourceLinks,
		ResourceType:  "COMPOSITE_COMPONENT",
	}

	jsonBody, err := json.Marshal(oc)
	utils.CheckBlockingError(err)
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

// RemoveApp removes application by specified name. It will return error
// in case the name is non-unique. Currently not being used because
// removing application by name is disabled. As second parameter takes
// boolean to specify if waiting for this task is needed.
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

// RemoveAppID removes application by it's ID. As second parameter takes
// boolean to specify if waiting for this task is needed.
// Usage of short unique IDs is supported for this operation.
func RemoveAppID(id string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	var (
		resLinks []string
		err      error
	)

	fullIds, err := selflink.GetFullIds([]string{id}, new(ListApps), utils.APPLICATION)
	utils.CheckBlockingError(err)
	resourceLinks := utils.CreateResLinksForApps(fullIds)
	oc := &containers.OperationContainer{
		Operation:     "Container.Delete",
		ResourceLinks: resourceLinks,
		ResourceType:  "COMPOSITE_COMPONENT",
	}
	jsonBody, err := json.Marshal(oc)
	utils.CheckBlockingError(err)
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

// RunApp provision template by specified name. It will return error
// in case the name is non-unique. Currently not being used because
// provisioning template by name is disabled. As second parameter takes
// boolean to specify if waiting for this task is needed.
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

// RunAppID provision template by it's ID. As second parameter takes
// boolean to specify if waiting for this task is needed.
// Usage of short unique IDs is supported for this operation.
func RunAppID(id, tenantId string, asyncTask bool) ([]string, error) {
	jsonBody := make(map[string]string, 0)
	fullId, err := selflink.GetFullId(id, new(templates.CompositeDescriptionList), utils.TEMPLATE)
	utils.CheckBlockingError(err)
	link := utils.CreateResLinkForTemplate(fullId)
	jsonBody["documentSelfLink"] = link
	reqBody, err := json.Marshal(jsonBody)
	utils.CheckBlockingError(err)

	url := config.URL + "/resources/composite-descriptions-clone"
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(reqBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	cd := &CompositeDescription{}
	err = json.Unmarshal(respBody, cd)
	utils.CheckBlockingError(err)

	link = cd.DocumentSelfLink

	ra := appProvisionOperation{
		ResourceDescriptionLink: link,
		ResourceType:            "COMPOSITE_COMPONENT",
	}
	ra.setTenantLink(tenantId)
	resLinks, err := ra.run(asyncTask)
	ids := utils.GetResourceIDs(resLinks)
	return ids, err
}

// RunAppFile is provisioning template from file.
// if keepTemplate parameter is true it will remove the
// imported template after provisioning is done, otherwise it will keep it.
func RunAppFile(dirF, tenantId string, keepTemplate, asyncTask bool) ([]string, error) {
	id, _ := templates.Import(dirF)
	resLinks, err := RunAppID(id, tenantId, false)
	if !keepTemplate {
		templates.RemoveTemplateID(id)
	}
	ids := utils.GetResourceIDs(resLinks)
	return ids, err
}

//queryTemplateName gets links of templates with name equal to passed in parameter.
func queryTemplateName(tmplName string) []string {
	tmplNameArr := strings.Split(tmplName, "/")
	name := tmplNameArr[len(tmplNameArr)-1]
	lt := templates.TemplatesList{}
	lt.FetchTemplates(name)
	links := lt.GetTemplateLinks(tmplName)
	return links

}

// appProvision structure is used to create object,
// which is needed to provision application from the description link.
type appProvisionOperation struct {
	ResourceDescriptionLink string   `json:"resourceDescriptionLink"`
	ResourceType            string   `json:"resourceType"`
	TenantLinks             []string `json:"tenantLinks"`
}

// run will provision application from the already set up
// appProvisionOperation object.
func (this *appProvisionOperation) run(asyncTask bool) ([]string, error) {
	var (
		links []string
		err   error
	)
	url := config.URL + "/requests"

	jsonBody, err := json.Marshal(this)
	utils.CheckBlockingError(err)

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

// setTenantLink will set up properly either
// the business group or project depending on the condition
// if the user is logged against admiral standalone or vRA mode.
func (this *appProvisionOperation) setTenantLink(tenantLinkId string) {
	tenantLinks := make([]string, 0)
	if tenantLinkId == "" {
		this.TenantLinks = nil
		return
	}
	if !utils.IsVraMode {
		fullProjectId, err := selflink.GetFullId(tenantLinkId, new(projects.ProjectList), utils.PROJECT)
		utils.CheckBlockingError(err)
		projectLink := utils.CreateResLinkForProject(fullProjectId)
		tenantLinks = append(tenantLinks, projectLink)
	} else {
		fullBusinessGroupId, err := businessgroups.GetFullId(tenantLinkId)
		utils.CheckBlockingError(err)
		businessGroupLink := utils.CreateResLinkForBusinessGroup(fullBusinessGroupId)
		tenantLinks = append(tenantLinks, businessGroupLink)
		tenantLinks = append(tenantLinks, "/tenants/"+utils.GetTenant())
	}
	this.TenantLinks = tenantLinks
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

// AppComponent structure is used just to hold data needed to print when
// inspecting application.
type AppComponent struct {
	ComponentType     string   `json:"ComponentType"`
	Id                string   `json:"ID"`
	NetworksConnected []string `json:"NetworksConnected,omitempty"`
}

// InspectApp structure is used just to hold data needed to print when
// inspecting application.
type InspectApp struct {
	Id         string          `json:"ID"`
	Name       string          `json:"Name"`
	Containers int             `json:"ContainersCount"`
	Networks   int             `json:"NetworksCount"`
	Components []*AppComponent `json:"Components"`
}

// InspectID returns string containing information about the application.
// that is being inspected. The string is JSON formatted.
// Usage of short unique IDs is supported for this operation.
func InspectID(id string) (string, error) {
	fullIds, err := selflink.GetFullIds([]string{id}, new(ListApps), utils.APPLICATION)
	utils.CheckBlockingError(err)
	resourceLinks := utils.CreateResLinksForApps(fullIds)
	url := config.URL + resourceLinks[0]
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	app := &App{}
	err = json.Unmarshal(respBody, app)
	utils.CheckBlockingError(err)

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
