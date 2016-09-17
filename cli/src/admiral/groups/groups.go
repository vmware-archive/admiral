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

package groups

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/functions"
)

type Group struct {
	Name             string `json:"name,omitempty"`
	Description      string `json:"description,omitempty"`
	DocumentSelfLink string `json:"documentSelfLink,omitempty"`
}

//GetID returns the ID of the Group.
func (g *Group) GetID() string {
	return strings.Replace(g.DocumentSelfLink, "/resources/groups/", "", -1)
}

type GroupList struct {
	DocumentLinks []string         `json:"documentLinks"`
	Documents     map[string]Group `json:"documents"`
}

//FetchGroups fetches all groups and return their count.
func (gl *GroupList) FetchGroups() int {
	url := config.URL + "/resources/groups?documentType=true&expand=true"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody := client.ProcessRequest(req)
	err := json.Unmarshal(respBody, gl)
	functions.CheckJson(err)
	return len(gl.DocumentLinks)
}

//Print prints already fetched groups.
func (gl *GroupList) Print() {
	if len(gl.DocumentLinks) < 1 {
		fmt.Println("n/a")
		return
	}
	fmt.Printf("%-40s %-25s \n", "ID", "NAME")
	for i := range gl.DocumentLinks {
		val := gl.Documents[gl.DocumentLinks[i]]
		fmt.Printf("%-40s %-25s \n", val.GetID(), val.Name)
	}
}

//AddGroup adds group and takes as parameters the name and description of the new group.
//Returns the ID of the new group and error. If the error is != nil the string for ID is empty.
func AddGroup(name, description string) (string, error) {
	if name == "" {
		return "", errors.New("Provide group name.")
	}

	group := &Group{
		Name:        name,
		Description: description,
	}

	jsonBody, err := json.Marshal(group)
	functions.CheckJson(err)

	url := config.URL + "/resources/groups"
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)

	if resp.StatusCode != 200 {
		return "", errors.New("Error occured when adding group.")
	}

	group = &Group{}
	err = json.Unmarshal(respBody, group)
	functions.CheckJson(err)
	return group.GetID(), nil
}

//RemoveGroup removes group by name. Returns the ID of the removed
//group and error which is != nil if none or more than one groups have the same name,
//or if the response code is different from 200.
func RemoveGroup(name string) (string, error) {
	links := GetGroupLinks(name)
	if len(links) < 1 {
		return "", errors.New("Group not found.")
	}
	if len(links) > 1 {
		return "", errors.New("Group with duplicate name found, provide ID to remove specific group.")
	}
	id := functions.GetResourceID(links[0])
	return RemoveGroupID(id)
}

//RemoveGroupID removes group by ID. Returns the ID of the removed group
//and error which is != nil if the response code is different from 200.
func RemoveGroupID(id string) (string, error) {
	url := config.URL + functions.CreateResLinkForGroup(id)
	req, _ := http.NewRequest("DELETE", url, nil)
	resp, _ := client.ProcessRequest(req)
	if resp.StatusCode != 200 {
		return "", errors.New("Error occured when removing group.")
	}
	return id, nil
}

//EditGroup edits group by name. The parameters it takes are
//the name of desired group to edit, new name and new description.
//In case you don't want to modify the property pass empty string. The function
//returns the ID of the edited string and error which is != nil if the none or more than one groups
//have the same name and if the response code is different from 200.
func EditGroup(name, newName, newDescription string) (string, error) {
	links := GetGroupLinks(name)
	if len(links) < 1 {
		return "", errors.New("Group not found.")
	}
	if len(links) > 1 {
		return "", errors.New("Group with duplicate name found, provide ID to update specific group.")
	}
	id := functions.GetResourceID(links[0])
	return EditGroupID(id, newName, newDescription)
}

//EditGroupID edits group by ID. The parameters it takes are
//the ID of desired group to edit, new name and new description.
//In case you don't want to modify the property pass empty string. The function
//returns the ID of the edited string and error which is != nil if the response code is different from 200.
func EditGroupID(id, newName, newDescription string) (string, error) {
	url := config.URL + functions.CreateResLinkForGroup(id)
	group := &Group{
		Name:        newName,
		Description: newDescription,
	}
	jsonBody, err := json.Marshal(group)
	functions.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	if resp.StatusCode != 200 {
		return "", errors.New("Error occured when adding group.")
	}
	group = &Group{}
	err = json.Unmarshal(respBody, group)
	functions.CheckJson(err)
	return group.GetID(), nil
}

//GetGroupLinks return array of self links of group
//that are matching the name passed as parameter.
func GetGroupLinks(name string) []string {
	links := make([]string, 0)
	gl := &GroupList{}
	gl.FetchGroups()
	for i := range gl.DocumentLinks {
		val := gl.Documents[gl.DocumentLinks[i]]
		if val.Name == name {
			links = append(links, val.DocumentSelfLink)
		}
	}
	return links
}

//GetGroupName takes self link of group as parameter and returns
//the name of that group.
func GetGroupName(link string) string {
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody := client.ProcessRequest(req)
	group := &Group{}
	err := json.Unmarshal(respBody, group)
	functions.CheckJson(err)
	return group.Name
}
