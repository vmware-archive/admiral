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

	"admiral/client"
	"admiral/config"
	"admiral/containers"
	"admiral/templates"
	"admiral/track"
	"admiral/utils"
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
	resourceLinks := utils.CreateResLinksForApps([]string{id})
	var (
		resLinks []string
		err      error
	)
	oc := &containers.OperationContainer{
		Operation:     "Container.Start",
		ResourceLinks: resourceLinks,
		ResourceType:  "COMPOSITE_COMPONENT",
	}

	jsonBody, err := json.Marshal(oc)
	utils.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr == nil {
		taskStatus := &track.OperationResponse{}
		_ = json.Unmarshal(respBody, taskStatus)
		taskStatus.PrintTracerId()
		if !asyncTask {
			resLinks, err = track.Wait(taskStatus.GetTracerId())
			resLinks = []string{id}
		} else {
			resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
			resLinks = []string{id}
		}
	} else {
		resLinks = nil
	}
	return resLinks, respErr
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
	resourceLinks := utils.CreateResLinksForApps([]string{id})
	var (
		resLinks []string
		err      error
	)
	oc := &containers.OperationContainer{
		Operation:     "Container.Stop",
		ResourceLinks: resourceLinks,
		ResourceType:  "COMPOSITE_COMPONENT",
	}

	jsonBody, err := json.Marshal(oc)
	utils.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr == nil {
		taskStatus := &track.OperationResponse{}
		_ = json.Unmarshal(respBody, taskStatus)
		taskStatus.PrintTracerId()
		if !asyncTask {
			resLinks, err = track.Wait(taskStatus.GetTracerId())
			resLinks = []string{id}
		} else {
			resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
			resLinks = []string{id}
		}
	} else {
		resLinks = nil
	}
	return resLinks, respErr
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
	resourceLinks := utils.CreateResLinksForApps([]string{id})
	var (
		resLinks []string
		err      error
	)
	oc := &containers.OperationContainer{
		Operation:     "Container.Delete",
		ResourceLinks: resourceLinks,
		ResourceType:  "COMPOSITE_COMPONENT",
	}
	jsonBody, err := json.Marshal(oc)
	utils.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr == nil {
		taskStatus := &track.OperationResponse{}
		_ = json.Unmarshal(respBody, taskStatus)
		taskStatus.PrintTracerId()
		if !asyncTask {
			resLinks, err = track.Wait(taskStatus.GetTracerId())
			resLinks = []string{id}
		} else {
			resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
			resLinks = []string{id}
		}
	} else {
		resLinks = nil
	}
	return resLinks, respErr
}

//Function to provision application.
//For parameter takes application name and bool to trigger or not waiting for task.
//Returns bool to specify if app is provisioning.
func RunApp(app string, asyncTask bool) ([]string, error) {
	links := queryTemplateName(app)
	if len(links) > 1 {
		return nil, templates.DuplicateNamesError
	} else if len(links) < 1 {
		return nil, templates.TemplateNotFoundError
	}

	id := utils.GetResourceID(links[0])
	return RunAppID(id, asyncTask)
}

//Same as RunApp() but takes app's ID in order to avoid conflict from duplicate names.
func RunAppID(id string, asyncTask bool) ([]string, error) {
	jsonBody := make(map[string]string, 0)
	link := "/resources/composite-descriptions/" + id
	jsonBody["documentSelfLink"] = link
	reqBody, err := json.Marshal(jsonBody)
	utils.CheckJson(err)

	url := config.URL + "/resources/composite-descriptions-clone"
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(reqBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	cd := &CompositeDescription{}
	err = json.Unmarshal(respBody, cd)
	utils.CheckJson(err)

	link = cd.DocumentSelfLink

	ra := RunApplication{
		ResourceDescriptionLink: link,
		ResourceType:            "COMPOSITE_COMPONENT",
	}
	resLinks, err := ra.run(asyncTask)
	ids := utils.GetResourceIDs(resLinks)
	return ids, err
}

func RunAppFile(dirF string, keepTemplate, asyncTask bool) ([]string, error) {
	id, _ := templates.Import(dirF)
	resLinks, err := RunAppID(id, false)
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

func InspectID(id string) bool {
	links := utils.CreateResLinksForApps([]string{id})
	url := config.URL + links[0]
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return false
	}
	app := &App{}
	err := json.Unmarshal(respBody, app)
	utils.CheckJson(err)
	customMap := make(map[string]App)
	customMap[id] = *app
	la := ListApps{
		Documents: customMap,
	}
	la.GetOutputStringWithContainers()
	return true
}

type RunApplication struct {
	ResourceDescriptionLink string `json:"resourceDescriptionLink"`
	ResourceType            string `json:"resourceType"`
}

//Function that send request to the Admiral API to provision application.
func (ra *RunApplication) run(asyncTask bool) ([]string, error) {
	var (
		links []string
		err   error
	)
	url := config.URL + "/requests"
	jsonBody, err := json.Marshal(ra)
	utils.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr == nil {
		taskStatus := &track.OperationResponse{}
		_ = json.Unmarshal(respBody, taskStatus)
		taskStatus.PrintTracerId()
		if !asyncTask {
			links, err = track.Wait(taskStatus.GetTracerId())
			return links, err
		} else {
			links, err = track.GetResLinks(taskStatus.GetTracerId())
			return links, err
		}
	}
	return nil, respErr
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

type CompositeDescription struct {
	DocumentSelfLink string `json:"documentSelfLink"`
}
