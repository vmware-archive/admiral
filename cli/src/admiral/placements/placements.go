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

package placements

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/deplPolicy"
	"admiral/functions"
	"admiral/projects"
	"admiral/resourcePools"
)

type Placement struct {
	Name                    string   `json:"name"`
	ResourcePoolLink        string   `json:"resourcePoolLink"`
	Priority                int32    `json:"priority"`
	ResourceType            string   `json:"resourceType"`
	MaxNumberInstances      int32    `json:"maxNumberInstances"`
	MemoryLimit             int64    `json:"memoryLimit"`
	StorageLimit            int32    `json:"storageLimit"`
	CpuShares               int32    `json:"cpuShares"`
	DeploymentPolicyLink    string   `json:"deploymentPolicyLink"`
	AvailableInstancesCount int32    `json:"availableInstancesCount"`
	AvailableMemory         int32    `json:"availableMemory"`
	TenantLinks             []string `json:"tenantLinks"`
	DocumentSelfLink        *string  `json:"documentSelfLink"`
	DocumentKind            string   `json:"documentKind,omitempty"`
}

func (p *Placement) GetID() string {
	return strings.Replace(*p.DocumentSelfLink, "/resources/group-placements/", "", -1)
}

type PlacementToAdd struct {
	Name                 string   `json:"name,omitempty"`
	ResourcePoolLink     string   `json:"resourcePoolLink,omitempty"`
	Priority             string   `json:"priority,omitempty"`
	ResourceType         string   `json:"resourceType,omitempty"`
	MaxNumberInstances   string   `json:"maxNumberInstances,omitempty"`
	MemoryLimit          int64    `json:"memoryLimit,omitmepty"`
	StorageLimit         string   `json:"storageLimit,omitempty"`
	CpuShares            string   `json:"cpuShares,omitempty"`
	DeploymentPolicyLink string   `json:"deploymentPolicyLink,omitempty"`
	TenantLinks          []string `json:"tenantLinks,omitempty"`
	DocumentKind         string   `json:"documentKind,omitempty"`

	AvailableInstancesCount      int32            `json:"availableInstancesCount,omitempty"`
	AllocatedInstancesCount      int32            `json:"allocatedInstancesCount"`
	AvailableMemory              int32            `json:"availableMemory,omitempty"`
	ResourceQuotaPerResourceDesc map[string]int32 `json:"resourceQuotaPerResourceDesc"`
}

type PlacementToUpdate struct {
	Name                 string   `json:"name,omitempty"`
	ResourcePoolLink     string   `json:"resourcePoolLink,omitempty"`
	Priority             int32    `json:"priority,omitempty"`
	ResourceType         string   `json:"resourceType,omitempty"`
	MaxNumberInstances   int32    `json:"maxNumberInstances,omitempty"`
	MemoryLimit          int64    `json:"memoryLimit,omitmepty"`
	StorageLimit         int32    `json:"storageLimit,omitempty"`
	CpuShares            int32    `json:"cpuShares,omitempty"`
	DeploymentPolicyLink string   `json:"deploymentPolicyLink,omitempty"`
	TenantLinks          []string `json:"tenantLinks,omitempty"`
	DocumentKind         string   `json:"documentKind,omitempty"`

	AvailableInstancesCount      int32            `json:"availableInstancesCount,omitempty"`
	AllocatedInstancesCount      int32            `json:"allocatedInstancesCount"`
	AvailableMemory              int32            `json:"availableMemory,omitempty"`
	ResourceQuotaPerResourceDesc map[string]int32 `json:"resourceQuotaPerResourceDesc"`
}

type PlacementList struct {
	DocumentLinks []string             `json:"documentLinks"`
	Documents     map[string]Placement `json:"documents"`
}

func (pl *PlacementList) FetchPlacements() (int, error) {
	url := config.URL + "/resources/group-placements?expand"

	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, pl)
	functions.CheckJson(err)
	return len(pl.DocumentLinks), nil
}

func (pl *PlacementList) GetOutputString() string {
	if len(pl.DocumentLinks) < 1 {
		return "No elements found."
	}
	var buffer bytes.Buffer
	buffer.WriteString("ID\tNAME\tPROJECT\tRESOURCE POOL\tDEPLOYMENT POLICY\tPRIORITY\tINSTANCES\tCPU SHARES\tMEMORY LIMIT")
	buffer.WriteString("\n")
	for _, link := range pl.DocumentLinks {
		val := pl.Documents[link]
		var (
			rp      string
			dp      string
			project string
		)

		if strings.TrimSpace(val.ResourcePoolLink) == "" {
			rp = ""
		} else {
			rp, _ = resourcePools.GetRPName(val.ResourcePoolLink)
		}

		if strings.TrimSpace(val.DeploymentPolicyLink) == "" {
			dp = ""
		} else {
			dp, _ = deplPolicy.GetDPName(val.DeploymentPolicyLink)
		}

		if len(val.TenantLinks) < 1 {
			project = ""
		} else {
			projectIndex := GetProjectLinkIndex(val.TenantLinks)
			if projectIndex != -1 && val.TenantLinks[projectIndex] != "" {
				project, _ = projects.GetProjectName(val.TenantLinks[0])
			} else {
				project = ""
			}
		}
		output := functions.GetFormattedString(val.GetID(), val.Name, project, rp, dp, val.Priority,
			val.AvailableInstancesCount, val.CpuShares, val.MemoryLimit)
		buffer.WriteString(output)
		buffer.WriteString("\n")

	}
	return strings.TrimSpace(buffer.String())
}

func RemovePlacement(polName string) (string, error) {
	polLinks := GetPlacementLinks(polName)
	if len(polLinks) > 1 {
		return "", errors.New("Placement with duplicate name found, provide ID to remove specific placement.")
	}
	if len(polLinks) < 1 {
		return "", errors.New("Placement not found.")
	}
	id := functions.GetResourceID(polLinks[0])
	return RemovePlacement(id)
}

func RemovePlacementID(id string) (string, error) {
	link := functions.CreateResLinksForPlacement(id)
	url := config.URL + link
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil
}

func AddPlacement(namePol, cpuShares, instances, priority, projectId, resPoolID, deplPolID string, memoryLimit int64) (string, error) {
	url := config.URL + "/resources/group-placements"
	var (
		dpLink      string
		rpLink      string
		projectLink string
	)

	if !haveNeeded(resPoolID) {
		return "", errors.New("Resource pool ID is required.")
	}

	if deplPolID != "" {
		dpLink = functions.CreateResLinkForDP(deplPolID)
	}

	rpLink = functions.CreateResLinkForRP(resPoolID)

	if projectId != "" {
		projectLink = functions.CreateResLinkForProject(projectId)
	}

	placement := PlacementToAdd{
		//Must
		Name:                 namePol,
		MaxNumberInstances:   instances,
		ResourcePoolLink:     rpLink,
		DeploymentPolicyLink: dpLink,
		TenantLinks:          []string{projectLink},
		//Optional
		CpuShares:   cpuShares,
		MemoryLimit: memoryLimit,
		Priority:    priority,
	}

	jsonBody, err := json.Marshal(placement)
	functions.CheckJson(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	newPolicy := &Placement{}
	err = json.Unmarshal(respBody, newPolicy)
	functions.CheckJson(err)
	return newPolicy.GetID(), nil
}

func EditPlacement(name, namePol, projectId, resPoolID, deplPolID string, cpuShares, instances, priority int32, memoryLimit int64) (string, error) {
	polLinks := GetPlacementLinks(name)
	if len(polLinks) > 1 {
		return "", errors.New("Placement with duplicate name found, provide ID to remove specific policy.")
	}
	if len(polLinks) < 1 {
		return "", errors.New("Placement not found.")
	}

	id := functions.GetResourceID(polLinks[0])
	return EditPlacementID(id, namePol, projectId, resPoolID, deplPolID, cpuShares, instances, priority, memoryLimit)
}

func EditPlacementID(id, namePol, projectId, resPoolID, deplPolID string, cpuShares, instances, priority int32, memoryLimit int64) (string, error) {
	url := config.URL + functions.CreateResLinksForPlacement(id)
	//Workaround
	oldPlacement := &PlacementToUpdate{}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	err := json.Unmarshal(respBody, oldPlacement)
	functions.CheckJson(err)
	//Workaround

	if cpuShares != -1 {
		oldPlacement.CpuShares = cpuShares
	}
	if instances != -1 {
		oldPlacement.MaxNumberInstances = instances
	}
	if namePol != "" {
		oldPlacement.Name = namePol
	}
	if priority != -1 {
		oldPlacement.Priority = priority
	}
	if projectId != "" {
		projectLinkIndex := GetProjectLinkIndex(oldPlacement.TenantLinks)
		projectLink := functions.CreateResLinkForProject(projectId)
		oldPlacement.TenantLinks[projectLinkIndex] = projectLink
	}
	if resPoolID != "" {
		oldPlacement.ResourcePoolLink = functions.CreateResLinkForRP(resPoolID)
	}
	if deplPolID != "" {
		oldPlacement.DeploymentPolicyLink = functions.CreateResLinkForDP(deplPolID)
	}
	if memoryLimit != 0 {
		oldPlacement.MemoryLimit = 0
	}

	jsonBody, err := json.Marshal(oldPlacement)
	functions.CheckJson(err)
	req, _ = http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr = client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	newPlacement := &Placement{}
	err = json.Unmarshal(respBody, newPlacement)
	functions.CheckJson(err)
	return newPlacement.GetID(), nil
}

func haveNeeded(resourcePool string) bool {
	if resourcePool == "" {
		return false
	}
	return true
}

func GetPlacementLinks(name string) []string {
	pl := &PlacementList{}
	pl.FetchPlacements()
	links := make([]string, 0)
	for key, val := range pl.Documents {
		if name == val.Name {
			links = append(links, key)
		}
	}
	return links
}

func GetProjectLinkIndex(tenantLinks []string) int {
	for i := range tenantLinks {
		if strings.Contains(tenantLinks[i], "/resources/groups/") {
			return i
		}
	}
	return -1
}
