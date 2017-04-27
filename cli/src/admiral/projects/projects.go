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

package projects

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/common"
	"admiral/common/base_types"
	"admiral/common/utils"
	"admiral/common/utils/selflink_utils"
	"admiral/common/utils/uri_utils"
	"admiral/config"
)

var (
	ProjectNameNotProvidedError = errors.New("Provide project name.")
	ProjectNotFoundError        = errors.New("Project not found.")
	DuplicateNamesError         = errors.New("Project with duplicate name found, provide ID to remove specific project.")
)

type Project struct {
	base_types.ServiceDocument

	Name string `json:"name,omitempty"`
}

//GetID returns the ID of the project.
func (g *Project) GetID() string {
	return strings.Replace(g.DocumentSelfLink, "/resources/groups/", "", -1)
}

type ProjectList struct {
	DocumentLinks []string           `json:"documentLinks"`
	Documents     map[string]Project `json:"documents"`
}

func (pl *ProjectList) GetCount() int {
	return len(pl.DocumentLinks)
}

func (pl *ProjectList) GetResource(index int) selflink_utils.Identifiable {
	resource := pl.Documents[pl.DocumentLinks[index]]
	return &resource
}

func (pl *ProjectList) Renew() {
	*pl = ProjectList{}
}

//FetchProjects fetches all projects and return their count.
func (gl *ProjectList) FetchProjects() (int, error) {
	url := uri_utils.BuildUrl(uri_utils.Project, uri_utils.GetCommonQueryMap(), true)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, gl)
	utils.CheckBlockingError(err)
	return len(gl.DocumentLinks), nil
}

//Print prints already fetched projects.
func (gl *ProjectList) GetOutputString() string {
	if gl.GetCount() < 1 {
		return selflink_utils.NoElementsFoundMessage
	}
	var buffer bytes.Buffer
	buffer.WriteString("ID\tNAME\n")
	for i := range gl.DocumentLinks {
		val := gl.Documents[gl.DocumentLinks[i]]
		output := utils.GetTabSeparatedString(val.GetID(), val.Name)
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

//AddProject adds project and takes as parameters the name and description of the new project.
//Returns the ID of the new project and error. If the error is != nil the string for ID is empty.
func AddProject(name string) (string, error) {
	if name == "" {
		return "", ProjectNameNotProvidedError
	}
	project := &Project{
		Name: name,
	}
	jsonBody, err := json.Marshal(project)
	utils.CheckBlockingError(err)

	url := uri_utils.BuildUrl(uri_utils.Project, nil, true)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	project = &Project{}
	err = json.Unmarshal(respBody, project)
	utils.CheckBlockingError(err)
	return project.GetID(), nil
}

//RemoveProjectID removes project by ID. Returns the ID of the removed project
//and error which is != nil if the response code is different from 200.
func RemoveProjectID(id string) (string, error) {
	fullId, err := selflink_utils.GetFullId(id, new(ProjectList), common.PROJECT)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForProject(fullId)
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return fullId, nil
}

//EditProjectID edits project by ID. The parameters it takes are
//the ID of desired project to edit, new name and new description.
//In case you don't want to modify the property pass empty string. The function
//returns the ID of the edited string and error which is != nil if the response code is different from 200.
func EditProjectID(id, newName string) (string, error) {
	fullId, err := selflink_utils.GetFullId(id, new(ProjectList), common.PROJECT)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForProject(fullId)
	project := &Project{
		Name: newName,
	}
	jsonBody, err := json.Marshal(project)
	utils.CheckBlockingError(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	req.Header.Set("Pragma", "xn-force-update-index")
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	project = &Project{}
	err = json.Unmarshal(respBody, project)
	utils.CheckBlockingError(err)
	return project.GetID(), nil
}

//GetProjectName takes self link of project as parameter and returns
//the name of that project.
func GetProjectName(link string) string {
	if link == "" {
		return ""
	}
	fullId, err := selflink_utils.GetFullId(link, new(ProjectList), common.PROJECT)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForProject(fullId)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	utils.CheckBlockingError(respErr)
	project := &Project{}
	err = json.Unmarshal(respBody, project)
	utils.CheckBlockingError(err)
	return project.Name
}
