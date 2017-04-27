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

	"admiral/business_groups"
	"admiral/client"
	"admiral/common"
	"admiral/common/utils"
	"admiral/common/utils/selflink_utils"
	"admiral/common/utils/uri_utils"
	"admiral/config"
	"admiral/containers"
	"admiral/projects"
	"admiral/templates"
	"admiral/track"
)

var (
	DuplicateNamesError      = errors.New("Duplicates found, provide the ID of the specific aplication.")
	ApplicationNotFoundError = errors.New("Application not found.")
)

// StartAppID starts application by it's ID. As second parameter takes
// boolean to specify if waiting for this task is needed.
// Usage of short unique IDs is supported for this operation.
func StartAppID(id string, asyncTask bool) ([]string, error) {
	url := uri_utils.BuildUrl(uri_utils.RequestBrokerService, nil, true)
	var (
		resLinks []string
		err      error
	)
	fullIds, err := selflink_utils.GetFullIds([]string{id}, new(ListApps), common.APPLICATION)
	utils.CheckBlockingError(err)

	resourceLinks := utils.CreateResLinksForApps(fullIds)
	oc := &containers.ContainersOperation{
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
		resLinks, err = track.StartWaitingFromResponseBody(respBody)
		return resLinks, err
	}
	return nil, nil
}

// StopAppID stops application by it's ID. As second parameter takes
// boolean to specify if waiting for this task is needed.
// Usage of short unique IDs is supported for this operation.
func StopAppID(id string, asyncTask bool) ([]string, error) {
	url := uri_utils.BuildUrl(uri_utils.RequestBrokerService, nil, true)
	var (
		resLinks []string
		err      error
	)
	fullIds, err := selflink_utils.GetFullIds([]string{id}, new(ListApps), common.APPLICATION)
	utils.CheckBlockingError(err)
	resourceLinks := utils.CreateResLinksForApps(fullIds)
	oc := &containers.ContainersOperation{
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
		resLinks, err = track.StartWaitingFromResponseBody(respBody)
		return resLinks, err
	}

	track.PrintTaskIdFromResponseBody(respBody)
	return nil, nil
}

// RemoveAppID removes application by it's ID. As second parameter takes
// boolean to specify if waiting for this task is needed.
// Usage of short unique IDs is supported for this operation.
func RemoveAppID(id string, asyncTask bool) ([]string, error) {
	url := uri_utils.BuildUrl(uri_utils.RequestBrokerService, nil, true)

	fullIds, err := selflink_utils.GetFullIds([]string{id}, new(ListApps), common.APPLICATION)
	utils.CheckBlockingError(err)
	resourceLinks := utils.CreateResLinksForApps(fullIds)
	oc := &containers.ContainersOperation{
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
		// Ignore resource links from RequestBrokerService, because we want to
		// return the ID of the applications instead of it's components.
		_, err = track.StartWaitingFromResponseBody(respBody)
		return fullIds, err
	}
	track.PrintTaskIdFromResponseBody(respBody)
	return nil, nil
}

type CompositeDescription struct {
	DocumentSelfLink string `json:"documentSelfLink"`
}

// RunAppID provision template by it's ID. As second parameter takes
// boolean to specify if waiting for this task is needed.
// Usage of short unique IDs is supported for this operation.
func RunAppID(id, tenantId string, asyncTask bool) ([]string, error) {
	jsonBody := make(map[string]string, 0)
	fullId, err := selflink_utils.GetFullId(id, new(templates.CompositeDescriptionList), common.TEMPLATE)
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
	url := uri_utils.BuildUrl(uri_utils.RequestBrokerService, nil, true)

	jsonBody, err := json.Marshal(this)
	utils.CheckBlockingError(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	if !asyncTask {
		links, err = track.StartWaitingFromResponseBody(respBody)
		return links, err
	}
	track.PrintTaskIdFromResponseBody(respBody)
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
		fullProjectId, err := selflink_utils.GetFullId(tenantLinkId, new(projects.ProjectList), common.PROJECT)
		utils.CheckBlockingError(err)
		projectLink := utils.CreateResLinkForProject(fullProjectId)
		tenantLinks = append(tenantLinks, projectLink)
	} else {
		fullBusinessGroupId, err := business_groups.GetFullId(tenantLinkId)
		utils.CheckBlockingError(err)
		businessGroupLink := utils.CreateResLinkForBusinessGroup(fullBusinessGroupId, config.TENANT)
		tenantLinks = append(tenantLinks, businessGroupLink)
		tenantLinks = append(tenantLinks, utils.CreateResLinkForTenant(config.TENANT))
	}
	this.TenantLinks = tenantLinks
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
	Closures   int             `json:"ClosuresCount"`
	Components []*AppComponent `json:"Components"`
}

// InspectID returns string containing information about the application.
// that is being inspected. The string is JSON formatted.
// Usage of short unique IDs is supported for this operation.
func InspectID(id string) (string, error) {
	fullIds, err := selflink_utils.GetFullIds([]string{id}, new(ListApps), common.APPLICATION)
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
		Closures:   app.GetClosuresCount(),
		Components: make([]*AppComponent, 0),
	}
	for i, contLink := range app.ComponentLinks {
		component := &AppComponent{}
		component.Id = utils.GetResourceID(contLink)
		componentType := app.GetComponentResourceType(i)
		if componentType == common.CONTAINER {
			component.ComponentType = componentType.GetName()
			c := containers.GetContainer(component.Id)
			component.NetworksConnected = utils.ValuesToStrings(utils.GetMapKeys(c.Networks))
		} else {
			component.ComponentType = componentType.GetName()
		}

		ia.Components = append(ia.Components, component)
	}
	jsonBody, _ := json.MarshalIndent(ia, "", "    ")
	return string(jsonBody), nil
}
