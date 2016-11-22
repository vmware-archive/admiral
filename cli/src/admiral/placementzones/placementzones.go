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
	"admiral/utils"
	"admiral/utils/selflink"
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
	DocumentSelfLink  string            `json:"documentSelfLink"`
}

func (pz *PlacementZone) GetID() string {
	return strings.Replace(pz.DocumentSelfLink, "/resources/pools/", "", -1)
}

type ResourcePoolState struct {
	Name             string             `json:"name,omitempty"`
	MaxCpuCount      int64              `json:"maxCpuCount"`
	MaxMemoryBytes   int64              `json:"maxMemoryBytes"`
	CustomProperties map[string]*string `json:"customProperties"`
	DocumentSelfLink string             `json:"documentSelfLink,omitempty"`
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

func (rpl *PlacementZoneList) FetchPZ() (int, error) {
	url := config.URL + "/resources/elastic-placement-zones-config?documentType=true&expand=true"
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
	buffer.WriteString("ID\tNAME\tMEMORY\tCPU\n")
	for _, link := range rpl.DocumentLinks {
		val := rpl.Documents[link]
		output := utils.GetTabSeparatedString(val.ResourcePoolState.GetID(), val.ResourcePoolState.Name,
			val.ResourcePoolState.GetUsedMemoryPercentage(), val.ResourcePoolState.GetUsedCpuPercentage())
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
	url := config.URL + utils.CreateResLinkForPlacementZone(fullId)
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil
}

func AddPZ(rpName string, custProps []string) (string, error) {
	url := config.URL + "/resources/elastic-placement-zones-config"
	cp := properties.ParseCustomProperties(custProps)
	pzState := ResourcePoolState{
		Name:             rpName,
		CustomProperties: cp,
	}
	pz := &PlacementZone{
		ResourcePoolState: pzState,
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

func EditPZ(pzName, newName string) (string, error) {
	links := GetPZLinks(pzName)
	if len(links) > 1 {
		return "", DuplicateNamesError
	} else if len(links) < 1 {
		return "", PlacementZoneNotFound
	}
	id := utils.GetResourceID(links[0])
	return EditPZID(id, newName)
}

func EditPZID(id, newName string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(PlacementZoneList), utils.PLACEMENT_ZONE)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinkForPlacementZone(fullId)
	pzState := ResourcePoolState{
		Name: newName,
	}
	jsonBody, _ := json.Marshal(pzState)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil
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
	utils.CheckBlockingError(err)
	return pzs.Name, nil
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
