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
	"admiral/placementzones"
	"admiral/projects"
	"admiral/utils"
	"admiral/utils/selflink"
	"fmt"
)

var (
	DuplicateNamesError        = errors.New("Placement with duplicate name found, provide ID to remove specific placement.")
	PlacementNotFoundError     = errors.New("Placement not found.")
	PlacementZoneRequiredError = errors.New("Placement zone ID is required.")
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

func (p *Placement) GetFormattedMemoryLimit() string {
	if p.MemoryLimit < 1000 {
		return fmt.Sprintf("%d BYTES", p.MemoryLimit)
	} else if p.MemoryLimit < 1000000 {
		return fmt.Sprintf("%.1f KB", float64(p.MemoryLimit)/(1000))
	} else if p.MemoryLimit < 1000000000 {
		return fmt.Sprintf("%.1f MB", float64(p.MemoryLimit)/(1000*1000))
	} else if p.MemoryLimit < 1000000000000 {
		return fmt.Sprintf("%.1f GB", float64(p.MemoryLimit)/(1000*1000*1000))
	}
	return fmt.Sprintf("%d", p.MemoryLimit)
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

func (pl *PlacementList) GetCount() int {
	return len(pl.DocumentLinks)
}

func (pl *PlacementList) GetResource(index int) selflink.Identifiable {
	resource := pl.Documents[pl.DocumentLinks[index]]
	return &resource
}

func (pl *PlacementList) FetchPlacements() (int, error) {
	url := config.URL + "/resources/group-placements?documentType=true&expand=true"

	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, pl)
	utils.CheckBlockingError(err)
	return len(pl.DocumentLinks), nil
}

func (pl *PlacementList) GetOutputString() string {
	if pl.GetCount() < 1 {
		return utils.NoElementsFoundMessage
	}
	var buffer bytes.Buffer
	buffer.WriteString("ID\tNAME\tPROJECT\tPLACEMENT ZONE\tPRIORITY\tINSTANCES\tCPU SHARES\tMEMORY LIMIT")
	buffer.WriteString("\n")
	for _, link := range pl.DocumentLinks {
		val := pl.Documents[link]
		var (
			pz      string
			project string
		)

		if strings.TrimSpace(val.ResourcePoolLink) == "" {
			pz = ""
		} else {
			pz, _ = placementzones.GetPZName(val.ResourcePoolLink)
		}

		// Currently disabled!
		//if strings.TrimSpace(val.DeploymentPolicyLink) == "" {
		//	dp = ""
		//} else {
		//	dp, _ = deplPolicy.GetDPName(val.DeploymentPolicyLink)
		//}

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
		output := utils.GetTabSeparatedString(val.GetID(), val.Name, project, pz, val.Priority,
			val.AvailableInstancesCount, val.CpuShares, val.GetFormattedMemoryLimit())
		buffer.WriteString(output)
		buffer.WriteString("\n")

	}
	return strings.TrimSpace(buffer.String())
}

func RemovePlacement(polName string) (string, error) {
	polLinks := GetPlacementLinks(polName)
	if len(polLinks) > 1 {
		return "", DuplicateNamesError
	}
	if len(polLinks) < 1 {
		return "", PlacementNotFoundError
	}
	id := utils.GetResourceID(polLinks[0])
	return RemovePlacement(id)
}

func RemovePlacementID(id string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(PlacementList), utils.PLACEMENT)
	utils.CheckBlockingError(err)
	link := utils.CreateResLinksForPlacement(fullId)
	url := config.URL + link
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil
}

func AddPlacement(namePol, cpuShares, instances, priority, projectId, placementZoneId, deplPolId string, memoryLimit int64) (string, error) {
	url := config.URL + "/resources/group-placements"
	var (
		err         error
		dpLink      string
		rpLink      string
		projectLink string
	)

	if !haveNeeded(placementZoneId) {
		return "", PlacementZoneRequiredError
	}

	if deplPolId != "" {
		var fullDpId string
		fullDpId, err = selflink.GetFullId(deplPolId, new(deplPolicy.DeploymentPolicyList), utils.DEPLOYMENT_POLICY)
		utils.CheckBlockingError(err)
		dpLink = utils.CreateResLinkForDP(fullDpId)
	}

	fullRpId, err := selflink.GetFullId(placementZoneId, new(placementzones.PlacementZoneList), utils.PLACEMENT_ZONE)
	utils.CheckBlockingError(err)
	rpLink = utils.CreateResLinkForPlacementZone(fullRpId)

	if projectId != "" {
		var fullProjectId string
		fullProjectId, err = selflink.GetFullId(projectId, new(projects.ProjectList), utils.PROJECT)
		utils.CheckBlockingError(err)
		projectLink = utils.CreateResLinkForProject(fullProjectId)
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
	utils.CheckBlockingError(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	newPolicy := &Placement{}
	err = json.Unmarshal(respBody, newPolicy)
	utils.CheckBlockingError(err)
	return newPolicy.GetID(), nil
}

func EditPlacement(name, namePol, projectId, resPoolID, deplPolID string, cpuShares, instances, priority int32, memoryLimit int64) (string, error) {
	polLinks := GetPlacementLinks(name)
	if len(polLinks) > 1 {
		return "", DuplicateNamesError
	}
	if len(polLinks) < 1 {
		return "", PlacementNotFoundError
	}

	id := utils.GetResourceID(polLinks[0])
	return EditPlacementID(id, namePol, projectId, resPoolID, deplPolID, cpuShares, instances, priority, memoryLimit)
}

func EditPlacementID(id, namePol, projectId, placementZoneID, deplPolId string, cpuShares, instances, priority int32, memoryLimit int64) (string, error) {
	fullId, err := selflink.GetFullId(id, new(PlacementList), utils.PLACEMENT)
	utils.CheckBlockingError(err)
	link := utils.CreateResLinksForPlacement(fullId)
	url := config.URL + link
	//Workaround
	oldPlacement := &PlacementToUpdate{}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	err = json.Unmarshal(respBody, oldPlacement)
	utils.CheckBlockingError(err)
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
		if len(oldPlacement.TenantLinks) == 0 || cap(oldPlacement.TenantLinks) == 0 {
			oldPlacement.TenantLinks = make([]string, 1)
		}
		projectLinkIndex := GetProjectLinkIndex(oldPlacement.TenantLinks)
		fullProjectId, err := selflink.GetFullId(projectId, new(projects.ProjectList), utils.PROJECT)
		utils.CheckBlockingError(err)
		projectLink := utils.CreateResLinkForProject(fullProjectId)
		oldPlacement.TenantLinks[projectLinkIndex] = projectLink
	}
	if placementZoneID != "" {
		fullPzId, err := selflink.GetFullId(placementZoneID, new(placementzones.PlacementZoneList), utils.PLACEMENT_ZONE)
		utils.CheckBlockingError(err)
		oldPlacement.ResourcePoolLink = utils.CreateResLinkForPlacementZone(fullPzId)
	}
	if deplPolId != "" {
		fullDpId, err := selflink.GetFullId(deplPolId, new(deplPolicy.DeploymentPolicyList), utils.DEPLOYMENT_POLICY)
		utils.CheckBlockingError(err)
		oldPlacement.DeploymentPolicyLink = utils.CreateResLinkForDP(fullDpId)
	}
	if memoryLimit != 0 {
		oldPlacement.MemoryLimit = memoryLimit
	}

	jsonBody, err := json.Marshal(oldPlacement)
	utils.CheckBlockingError(err)
	req, _ = http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr = client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	newPlacement := &Placement{}
	err = json.Unmarshal(respBody, newPlacement)
	utils.CheckBlockingError(err)
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
	return 0
}
