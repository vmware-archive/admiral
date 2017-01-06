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
	"fmt"
	"net/http"
	"strings"

	"admiral/businessgroups"
	"admiral/client"
	"admiral/config"
	"admiral/deplPolicy"
	"admiral/placementzones"
	"admiral/projects"
	"admiral/utils"
	"admiral/utils/selflink"
	"admiral/utils/urlutils"
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

func (p *Placement) GetTenantOrProjectName() string {
	tenantId := p.GetTenantId()
	if utils.IsVraMode {
		return businessgroups.GetBusinessGroupName(tenantId)
	} else {
		return projects.GetProjectName(tenantId)
	}
}

func (p *Placement) GetTenantId() string {
	for _, t := range p.TenantLinks {
		if utils.IsVraMode {
			if strings.Contains(t, fmt.Sprintf("/tenants/%s/groups/", config.TENANT)) {
				return utils.GetResourceID(t)
			}
		} else {
			if strings.Contains(t, urlutils.Project.GetBaseUrl()) {
				return utils.GetResourceID(t)
			}
		}
	}
	return ""
}

func (p *Placement) GetDeploymentPolicyName() string {
	if p.DeploymentPolicyLink == "" {
		return ""
	}
	dpName := deplPolicy.GetDPName(utils.GetResourceID(p.DeploymentPolicyLink))
	return dpName
}

type PlacementToAdd struct {
	Name                 string          `json:"name,omitempty"`
	ResourcePoolLink     string          `json:"resourcePoolLink,omitempty"`
	Priority             string          `json:"priority,omitempty"`
	ResourceType         string          `json:"resourceType,omitempty"`
	MaxNumberInstances   int64           `json:"maxNumberInstances"`
	MemoryLimit          int64           `json:"memoryLimit"`
	StorageLimit         string          `json:"storageLimit,omitempty"`
	CpuShares            string          `json:"cpuShares,omitempty"`
	DeploymentPolicyLink utils.NilString `json:"deploymentPolicyLink,omitempty"`
	TenantLinks          []string        `json:"tenantLinks,omitempty"`
	DocumentKind         string          `json:"documentKind,omitempty"`

	AvailableInstancesCount      int32            `json:"availableInstancesCount,omitempty"`
	AllocatedInstancesCount      int32            `json:"allocatedInstancesCount,omitempty"`
	AvailableMemory              int32            `json:"availableMemory,omitempty"`
	ResourceQuotaPerResourceDesc map[string]int32 `json:"resourceQuotaPerResourceDesc,omitempty"`
}

func (pta *PlacementToAdd) SetTenantLinks(tenantLink string) {
	if tenantLink == "" {
		return
	}
	if utils.IsVraMode {
		fullBusinessGroupId, err := businessgroups.GetFullId(tenantLink)
		utils.CheckBlockingError(err)
		businessGroupLink := utils.CreateResLinkForBusinessGroup(fullBusinessGroupId, config.TENANT)
		tenantLinks := make([]string, 0)
		tenantLinks = append(tenantLinks, businessGroupLink)
		tenantLinks = append(tenantLinks, utils.CreateResLinkForTenant(config.TENANT))
		pta.TenantLinks = tenantLinks
	} else {
		fullProjectId, err := selflink.GetFullId(tenantLink, new(projects.ProjectList), utils.PROJECT)
		utils.CheckBlockingError(err)
		projectLink := utils.CreateResLinkForProject(fullProjectId)
		pta.TenantLinks = []string{projectLink}
	}
}

func (pta *PlacementToAdd) SetDeploymentPolicy(dpId string) {
	dpLink := utils.NilString{}
	if dpId == "" {
		dpLink.Value = ""
	} else {
		fullDpId, err := selflink.GetFullId(dpId, new(deplPolicy.DeploymentPolicyList), utils.DEPLOYMENT_POLICY)
		utils.CheckBlockingError(err)
		dpLink.Value = utils.CreateResLinkForDeploymentPolicies(fullDpId)
	}
	pta.DeploymentPolicyLink = dpLink
}

func (pta *PlacementToAdd) SetResourcePool(rpId string) {
	fullRpId, err := selflink.GetFullId(rpId, new(placementzones.PlacementZoneList), utils.PLACEMENT_ZONE)
	utils.CheckBlockingError(err)
	pta.ResourcePoolLink = utils.CreateResLinkForResourcePool(fullRpId)
}

type PlacementToUpdate struct {
	Name                 string   `json:"name,omitempty"`
	ResourcePoolLink     string   `json:"resourcePoolLink,omitempty"`
	Priority             int32    `json:"priority,omitempty"`
	ResourceType         string   `json:"resourceType,omitempty"`
	MaxNumberInstances   int32    `json:"maxNumberInstances"`
	MemoryLimit          int64    `json:"memoryLimit"`
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

func (pta *PlacementToUpdate) SetTenantLinks(tenantLink string) {
	if tenantLink == "" {
		return
	}
	if utils.IsVraMode {
		fullBusinessGroupId, err := businessgroups.GetFullId(tenantLink)
		utils.CheckBlockingError(err)
		businessGroupLink := utils.CreateResLinkForBusinessGroup(fullBusinessGroupId, config.TENANT)
		tenantLinks := make([]string, 0)
		tenantLinks = append(tenantLinks, businessGroupLink)
		tenantLinks = append(tenantLinks, utils.CreateResLinkForTenant(config.TENANT))
		pta.TenantLinks = tenantLinks
	} else {
		fullProjectId, err := selflink.GetFullId(tenantLink, new(projects.ProjectList), utils.PROJECT)
		utils.CheckBlockingError(err)
		projectLink := utils.CreateResLinkForProject(fullProjectId)
		pta.TenantLinks = []string{projectLink}
	}
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

func (pl *PlacementList) Renew() {
	*pl = PlacementList{}
}

func (pl *PlacementList) FetchPlacements() (int, error) {
	url := urlutils.BuildUrl(urlutils.Placement, urlutils.GetCommonQueryMap(), true)

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
	if utils.IsVraMode {
		buffer.WriteString("ID\tNAME\tBUSINESS GROUP\tPLACEMENT ZONE\tDEPLOYMENT POLICY\tPRIORITY\tINSTANCES\tCPU SHARES\tMEMORY LIMIT")
		buffer.WriteString("\n")
	} else {
		buffer.WriteString("ID\tNAME\tPROJECT\tPLACEMENT ZONE\tPRIORITY\tINSTANCES\tCPU SHARES\tMEMORY LIMIT")
		buffer.WriteString("\n")
	}
	for _, link := range pl.DocumentLinks {
		val := pl.Documents[link]
		var placementZone string

		if strings.TrimSpace(val.ResourcePoolLink) == "" {
			placementZone = ""
		} else {
			placementZone, _ = placementzones.GetPZName(val.ResourcePoolLink)
		}

		if utils.IsVraMode {
			businessGroup := val.GetTenantOrProjectName()
			deploymentPolicy := val.GetDeploymentPolicyName()
			output := utils.GetTabSeparatedString(val.GetID(), val.Name, businessGroup, placementZone, deploymentPolicy, val.Priority,
				val.AvailableInstancesCount, val.CpuShares, val.GetFormattedMemoryLimit())
			buffer.WriteString(output)
			buffer.WriteString("\n")
		} else {
			output := utils.GetTabSeparatedString(val.GetID(), val.Name, val.GetTenantOrProjectName(), placementZone, val.Priority,
				val.AvailableInstancesCount, val.CpuShares, val.GetFormattedMemoryLimit())
			buffer.WriteString(output)
			buffer.WriteString("\n")
		}
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
	return fullId, nil
}

func AddPlacement(namePol, cpuShares, priority, projectId, placementZoneId, deplPolId string, memoryLimit, instances int64) (string, error) {
	url := urlutils.BuildUrl(urlutils.Placement, nil, true)
	var (
		err error
	)
	if !haveNeeded(placementZoneId) {
		return "", PlacementZoneRequiredError
	}

	placement := PlacementToAdd{
		Name:               namePol,
		MaxNumberInstances: instances,
		ResourceType:       "DOCKER_CONTAINER",
		CpuShares:          cpuShares,
		MemoryLimit:        memoryLimit,
		Priority:           priority,
	}
	placement.SetTenantLinks(projectId)
	placement.SetDeploymentPolicy(deplPolId)
	placement.SetResourcePool(placementZoneId)

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
	if placementZoneID != "" {
		fullPzId, err := selflink.GetFullId(placementZoneID, new(placementzones.PlacementZoneList), utils.PLACEMENT_ZONE)
		utils.CheckBlockingError(err)
		oldPlacement.ResourcePoolLink = utils.CreateResLinkForResourcePool(fullPzId)
	}
	if deplPolId != "" {
		fullDpId, err := selflink.GetFullId(deplPolId, new(deplPolicy.DeploymentPolicyList), utils.DEPLOYMENT_POLICY)
		utils.CheckBlockingError(err)
		oldPlacement.DeploymentPolicyLink = utils.CreateResLinkForDeploymentPolicies(fullDpId)
	}
	if memoryLimit != 0 {
		oldPlacement.MemoryLimit = memoryLimit
	}

	oldPlacement.SetTenantLinks(projectId)

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
