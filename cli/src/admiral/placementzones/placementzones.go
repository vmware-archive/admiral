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

package placementzones

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/properties"
	"admiral/tags"
	"admiral/utils"
	"admiral/utils/selflink"
	"admiral/utils/urlutils"
	"fmt"
	"os"
	"strconv"
)

var (
	DuplicateNamesError   = errors.New("Placement zones with duplicate name found, provide ID to remove specific placement zone.")
	PlacementZoneNotFound = errors.New("Placement zone not found.")
)

type PlacementZone struct {
	ResourcePoolState ResourcePoolState `json:"resourcePoolState"`
	EpzState          EpzState          `json:"epzState,omitempty"`
	DocumentSelfLink  string            `json:"documentSelfLink,omitempty"`

	DocumentVersion              int `json:"documentVersion"`
	DocumentUpdateTimeMicros     int `json:"documentUpdateTimeMicros"`
	DocumentExpirationTimeMicros int `json:"documentExpirationTimeMicros"`
}

func (pz *PlacementZone) GetID() string {
	return strings.Replace(pz.DocumentSelfLink, "/resources/pools/", "", -1)
}

type ResourcePoolState struct {
	Name             string             `json:"name,omitempty"`
	MaxCpuCount      int64              `json:"maxCpuCount,omitempty"`
	MaxMemoryBytes   int64              `json:"maxMemoryBytes,omitempty"`
	CustomProperties map[string]*string `json:"customProperties,omitempty"`
	TagLinks         []string           `json:"tagLinks"`
	DocumentSelfLink string             `json:"documentSelfLink,omitempty"`

	DescriptionLink              string      `json:"descriptionLink,omitempty"`
	DocumentVersion              int         `json:"documentVersion,omitempty"`
	DocumentEpoch                int         `json:"documentEpoch,omitempty"`
	DocumentKind                 string      `json:"documentKind,omitempty"`
	DocumentUpdateTimeMicros     interface{} `json:"documentUpdateTimeMicros,omitempty"`
	DocumentUpdateAction         string      `json:"documentUpdateAction,omitempty"`
	DocumentExpirationTimeMicros interface{} `json:"documentExpirationTimeMicros,omitempty"`
	DocumentOwner                string      `json:"documentOwner,omitempty"`
}

func (rps *ResourcePoolState) GetID() string {
	return strings.Replace(rps.DocumentSelfLink, "/resources/pools/", "", -1)
}

func (rps *ResourcePoolState) GetUsedMemoryPercentage() string {
	var (
		maxMemory       int64
		availableMemory int64
		usedMemory      int64
		err             error
	)
	maxMemory = rps.MaxMemoryBytes
	if am, ok := rps.CustomProperties["__availableMemory"]; !ok || am == nil {
		availableMemory = 0
	} else {
		availableMemory, err = strconv.ParseInt(*am, 10, 64)
	}
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(1)
	}
	usedMemory = maxMemory - availableMemory
	percentage := 0.0
	if maxMemory != 0 {
		percentage = (float64(usedMemory) / float64(maxMemory)) * 100
	}
	return fmt.Sprintf("%.2f%%", utils.MathRound(percentage*100)/100)
}

func (rps *ResourcePoolState) GetUsedCpuPercentage() string {
	if ac, ok := rps.CustomProperties["__cpuUsage"]; !ok || ac == nil {
		return "0%"
	}
	result, err := strconv.ParseFloat(*rps.CustomProperties["__cpuUsage"], 64)
	if err != nil {
		return "0%"
	}
	return fmt.Sprintf("%.2f%%", utils.MathRound(result*100)/100)
}

func (rps *ResourcePoolState) AddTagLinks(tagsInput []string) error {
	if rps.TagLinks == nil {
		rps.TagLinks = make([]string, 0)
	}
	for _, ti := range tagsInput {
		tagId, err := tags.GetTagIdByEqualKeyVals(ti, true)
		if err != nil {
			return err
		}
		tagLink := utils.CreateResLinkForTag(tagId)
		if tagLink != "" && !rps.containsTagLink(tagLink) {
			rps.TagLinks = append(rps.TagLinks, tagLink)
		}
	}
	return nil
}

func (rps *ResourcePoolState) RemoveTagLinks(tagsInput []string) error {
	tagsToRemove := make([]string, 0)
	for _, ti := range tagsInput {
		tagId, err := tags.GetTagIdByEqualKeyVals(ti, false)
		if err != nil {
			return err
		}
		if tagId != "" {
			tagLink := utils.CreateResLinkForTag(tagId)
			tagsToRemove = append(tagsToRemove, tagLink)
		}
	}

	for _, tagToRemove := range tagsToRemove {
		for i := 0; i < len(rps.TagLinks); i++ {
			if tagToRemove == rps.TagLinks[i] {
				rps.TagLinks = append(rps.TagLinks[:i], rps.TagLinks[i+1:]...)
				i--
			}
		}
	}

	return nil
}

func (rps *ResourcePoolState) containsTagLink(tagLink string) bool {
	for _, tl := range rps.TagLinks {
		if tl == tagLink {
			return true
		}
	}
	return false
}

type EpzState struct {
	ResourcePoolLink string   `json:"resourcePoolLink,omitempty"`
	TagLinksToMatch  []string `json:"tagLinksToMatch,omitempty"`
	DocumentSelfLink string   `json:"documentSelfLink,omitempty"`

	DocumentVersion              int `json:"documentVersion"`
	DocumentUpdateTimeMicros     int `json:"documentUpdateTimeMicros"`
	DocumentExpirationTimeMicros int `json:"documentExpirationTimeMicros"`
}

func (epzs *EpzState) AddTagLinks(tagsInput []string) error {
	if epzs.TagLinksToMatch == nil {
		epzs.TagLinksToMatch = make([]string, 0)
	}
	for _, ti := range tagsInput {
		tagId, err := tags.GetTagIdByEqualKeyVals(ti, true)
		if err != nil {
			return err
		}
		tagLink := utils.CreateResLinkForTag(tagId)
		if tagLink != "" && !epzs.containsTagLink(tagLink) {
			epzs.TagLinksToMatch = append(epzs.TagLinksToMatch, tagLink)
		}
	}
	return nil
}

func (epzs *EpzState) RemoveTagLinks(tagsInput []string) error {
	tagsToRemove := make([]string, 0)
	for _, ti := range tagsInput {
		tagId, err := tags.GetTagIdByEqualKeyVals(ti, false)
		if err != nil {
			return err
		}
		if tagId != "" {
			tagLink := utils.CreateResLinkForTag(tagId)
			tagsToRemove = append(tagsToRemove, tagLink)
		}
	}

	for _, tagToRemove := range tagsToRemove {
		for i := 0; i < len(epzs.TagLinksToMatch); i++ {
			if tagToRemove == epzs.TagLinksToMatch[i] {
				epzs.TagLinksToMatch = append(epzs.TagLinksToMatch[:i], epzs.TagLinksToMatch[i+1:]...)
				i--
			}
		}
	}

	return nil
}

func (epzs *EpzState) containsTagLink(tagLink string) bool {
	for _, tl := range epzs.TagLinksToMatch {
		if tl == tagLink {
			return true
		}
	}
	return false
}

func (epzs *EpzState) IsNullable() bool {
	if len(epzs.TagLinksToMatch) == 0 && epzs.DocumentSelfLink == "" && epzs.ResourcePoolLink == "" {
		return true
	}
	return false
}

func (epzs *EpzState) MarshalJSON() ([]byte, error) {
	if epzs.IsNullable() {
		return json.Marshal(nil)
	}
	return json.Marshal(*epzs)
}

type PlacementZoneList struct {
	TotalCount    int32                    `json:"totalCount"`
	Documents     map[string]PlacementZone `json:"documents"`
	DocumentLinks []string                 `json:"documentLinks"`
}

func (pzl *PlacementZoneList) GetCount() int {
	return len(pzl.DocumentLinks)
}

func (pzl *PlacementZoneList) GetResource(index int) selflink.Identifiable {
	resource := pzl.Documents[pzl.DocumentLinks[index]]
	return &resource
}

func (pzl *PlacementZoneList) Renew() {
	*pzl = PlacementZoneList{}
}

func (rpl *PlacementZoneList) FetchPZ() (int, error) {
	url := urlutils.BuildUrl(urlutils.ElasticPlacementZone, urlutils.GetCommonQueryMap(), true)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, rpl)
	utils.CheckBlockingError(err)
	return len(rpl.Documents), nil
}

func (rpl *PlacementZoneList) GetOutputString() string {
	var buffer bytes.Buffer
	if rpl.GetCount() < 1 {
		return utils.NoElementsFoundMessage
	}
	buffer.WriteString("ID\tNAME\tMEMORY\tCPU\tTAGS\n")
	for _, link := range rpl.DocumentLinks {
		val := rpl.Documents[link]
		output := utils.GetTabSeparatedString(val.ResourcePoolState.GetID(), val.ResourcePoolState.Name,
			val.ResourcePoolState.GetUsedMemoryPercentage(), val.ResourcePoolState.GetUsedCpuPercentage(),
			tags.TagsToString(val.ResourcePoolState.TagLinks))
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}

	return strings.TrimSpace(buffer.String())
}

func RemovePZ(pzName string) (string, error) {
	links := GetPZLinks(pzName)
	if len(links) > 1 {
		return "", DuplicateNamesError
	} else if len(links) < 1 {
		return "", PlacementZoneNotFound
	}
	id := utils.GetResourceID(links[0])
	return RemovePZID(id)
}

func RemovePZID(id string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(PlacementZoneList), utils.PLACEMENT_ZONE)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForResourcePool(fullId)
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return fullId, nil
}

func AddPZ(rpName string, custProps, tags, tagsToMatch []string) (string, error) {
	url := urlutils.BuildUrl(urlutils.ElasticPlacementZone, nil, true)

	cp := make(map[string]*string, 0)
	properties.ParseCustomProperties(custProps, cp)

	resPoolState := ResourcePoolState{
		Name:             rpName,
		CustomProperties: cp,
	}
	resPoolState.AddTagLinks(tags)

	epzState := EpzState{}
	epzState.AddTagLinks(tagsToMatch)

	pz := &PlacementZone{
		ResourcePoolState: resPoolState,
		EpzState:          epzState,
	}

	jsonBody, _ := json.Marshal(pz)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	pz = &PlacementZone{}
	err := json.Unmarshal(respBody, pz)
	utils.CheckBlockingError(err)
	return pz.ResourcePoolState.GetID(), nil

}

func EditPZ(pzName, newName string, tagsToAdd, tagsToRemove, tagsToMatchToAdd, tagsToMatchToRemove []string) (string, error) {
	links := GetPZLinks(pzName)
	if len(links) > 1 {
		return "", DuplicateNamesError
	} else if len(links) < 1 {
		return "", PlacementZoneNotFound
	}
	id := utils.GetResourceID(links[0])
	return EditPZID(id, newName, tagsToAdd, tagsToRemove, tagsToMatchToAdd, tagsToMatchToRemove)
}

func EditPZID(id, newName string, tagsToAdd, tagsToRemove, tagsToMatchToAdd, tagsToMatchToRemove []string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(PlacementZoneList), utils.PLACEMENT_ZONE)

	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForPlacementZone(utils.CreateResLinkForResourcePool(fullId))

	oldPz, _ := GetPlacementZone(id)

	if newName != "" {
		oldPz.ResourcePoolState.Name = newName
	}

	if len(tagsToMatchToRemove) > 0 {
		err = oldPz.EpzState.RemoveTagLinks(tagsToMatchToRemove)
		if err != nil {
			return "", err
		}
	}
	if len(tagsToMatchToAdd) > 0 {
		err = oldPz.EpzState.AddTagLinks(tagsToMatchToAdd)
		if err != nil {
			return "", err
		}
	}

	jsonBody, _ := json.Marshal(oldPz)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}

	err = handleTagLinksUpdate(fullId, tagsToAdd, tagsToRemove)

	if err != nil {
		return "", err
	}

	return fullId, nil
}

func handleTagLinksUpdate(placementZoneId string, tagsToAdd, tagsToRemove []string) error {
	if utils.IsNilOrEmptyStr(tagsToAdd) && utils.IsNilOrEmptyStr(tagsToRemove) {
		return nil
	}

	tagsToAssign := parseTagsInput(tagsToAdd)
	tagsToUnassign := parseTagsInput(tagsToRemove)

	fullId, err := selflink.GetFullId(placementZoneId, new(PlacementZoneList), utils.PLACEMENT_ZONE)

	if err != nil {
		return err
	}

	tagsRequest := &tags.TagAssignmentRequest{
		ResourceLink:   utils.CreateResLinkForResourcePool(fullId),
		TagsToAssign:   tagsToAssign,
		TagsToUnassign: tagsToUnassign,
	}

	url := urlutils.BuildUrl(urlutils.TagAssignment, nil, true)
	jsonBody, _ := json.Marshal(tagsRequest)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return respErr
	}
	return nil
}

func parseTagsInput(tagsInput []string) []tags.Tag {
	result := []tags.Tag{}

	for _, input := range tagsInput {
		tag, err := tags.NewTag(input)
		if err != nil {
			continue
		}
		result = append(result, *tag)
	}

	if len(result) < 1 {
		return nil
	}
	return result
}

func GetPZLinks(pzName string) []string {
	pzl := PlacementZoneList{}
	pzl.FetchPZ()
	links := make([]string, 0)
	for key, val := range pzl.Documents {
		if val.ResourcePoolState.Name == pzName {
			links = append(links, key)
		}
	}
	return links
}

func GetPZName(link string) (string, error) {
	url := config.URL + link
	pzs := &ResourcePoolState{}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	err := json.Unmarshal(respBody, pzs)
	if err != nil {
		return "", err
	}
	return pzs.Name, nil
}

func GetPlacementZone(id string) (*PlacementZone, error) {
	fullId, err := selflink.GetFullId(id, new(PlacementZoneList), utils.PLACEMENT_ZONE)
	utils.CheckBlockingError(err)
	url := config.URL + utils.GetIdFilterUrl(fullId, utils.PLACEMENT_ZONE)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	pzList := &PlacementZoneList{}
	err = json.Unmarshal(respBody, pzList)
	if err != nil {
		return nil, err
	}

	pz := pzList.Documents[pzList.DocumentLinks[0]]
	return &pz, nil
}

// Currently disabled!
//func GetCustomProperties(id string) (map[string]*string, error) {
//	link := functions.CreateResLinkForRP(id)
//	url := config.URL + link
//	req, _ := http.NewRequest("GET", url, nil)
//	_, respBody, respErr := client.ProcessRequest(req)
//	if respErr != nil {
//		return nil, respErr
//	}
//	placementZone := &PlacementZone{}
//	err := json.Unmarshal(respBody, placementZone)
//	functions.CheckJson(err)
//	return placementZone.PlacementZoneState.CustomProperties, nil
//}

// Currently disabled!
//func GetPublicCustomProperties(id string) (map[string]string, error) {
//	custProps, err := GetCustomProperties(id)
//	if custProps == nil {
//		return nil, err
//	}
//	publicCustProps := make(map[string]string)
//	for key, val := range custProps {
//		if len(key) > 2 {
//			if key[0:2] == "__" {
//				continue
//			}
//		}
//		publicCustProps[key] = *val
//	}
//	return publicCustProps, nil
//}

// Currently disabled!
//func AddCustomProperties(id string, keys, vals []string) error {
//	link := functions.CreateResLinkForRP(id)
//	url := config.URL + link
//	var lowerLen []string
//	if len(keys) > len(vals) {
//		lowerLen = vals
//	} else {
//		lowerLen = keys
//	}
//	custProps := make(map[string]*string)
//	for i, _ := range lowerLen {
//		custProps[keys[i]] = &vals[i]
//	}
//	pzState := &PlacementZoneState{
//		CustomProperties: custProps,
//	}
//	pz := &PlacementZone{
//		PlacementZoneState: *pzState,
//	}
//	jsonBody, err := json.Marshal(pz)
//	functions.CheckJson(err)
//	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
//	_, _, respErr := client.ProcessRequest(req)
//
//	if respErr != nil {
//		return respErr
//	}
//	return nil
//}

// Currently disabled!
//func RemoveCustomProperties(id string, keys []string) error {
//	link := functions.CreateResLinkForRP(id)
//	url := config.URL + link
//	custProps := make(map[string]*string)
//	for i := range keys {
//		custProps[keys[i]] = nil
//	}
//	pzState := &PlacementZoneState{
//		CustomProperties: custProps,
//	}
//	pz := &PlacementZone{
//		PlacementZoneState: *pzState,
//	}
//	jsonBody, err := json.Marshal(pz)
//	functions.CheckJson(err)
//	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
//	_, _, respErr := client.ProcessRequest(req)
//
//	if respErr != nil {
//		return respErr
//	}
//	return nil
//}
