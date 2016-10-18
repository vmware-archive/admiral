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
	"admiral/functions"
	"admiral/properties"
)

var (
	DuplicateNamesError   = errors.New("Placement zones with duplicate name found, provide ID to remove specific placement zone.")
	PlacementZoneNotFound = errors.New("Placement zone not found.")
)

type PlacementZone struct {
	PlacementZoneState PlacementZoneState `json:"resourcePoolState"`
}

type PlacementZoneList struct {
	TotalCount    int32                    `json:"totalCount"`
	Documents     map[string]PlacementZone `json:"documents"`
	DocumentLinks []string                 `json:"documentLinks"`
}

type PlacementZoneState struct {
	Name             string             `json:"name,omitempty"`
	CustomProperties map[string]*string `json:"customProperties"`
	DocumentSelfLink string             `json:"documentSelfLink,omitempty"`
}

func (pzs *PlacementZoneState) GetID() string {
	return strings.Replace(pzs.DocumentSelfLink, "/resources/pools/", "", -1)
}

func (rpl *PlacementZoneList) FetchPZ() (int, error) {
	url := config.URL + "/resources/elastic-placement-zones-config?documentType=true&expand=true"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, rpl)
	functions.CheckJson(err)
	return len(rpl.Documents), nil
}

func (rpl *PlacementZoneList) GetOutputString() string {
	var buffer bytes.Buffer
	if len(rpl.Documents) > 0 {
		buffer.WriteString("ID\tNAME\n")
		for _, link := range rpl.DocumentLinks {
			val := rpl.Documents[link]
			output := functions.GetFormattedString(val.PlacementZoneState.GetID(), val.PlacementZoneState.Name)
			buffer.WriteString(output)
			buffer.WriteString("\n")
		}
	} else {
		buffer.WriteString("No elements found.")
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
	id := functions.GetResourceID(links[0])
	return RemovePZID(id)
}

func RemovePZID(id string) (string, error) {
	url := config.URL + functions.CreateResLinkForPlacementZone(id)
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
	pzState := PlacementZoneState{
		Name:             rpName,
		CustomProperties: cp,
	}
	pz := &PlacementZone{
		PlacementZoneState: pzState,
	}
	jsonBody, _ := json.Marshal(pz)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	pz = &PlacementZone{}
	err := json.Unmarshal(respBody, pz)
	functions.CheckJson(err)
	return pz.PlacementZoneState.GetID(), nil

}

func EditPZ(pzName, newName string) (string, error) {
	links := GetPZLinks(pzName)
	if len(links) > 1 {
		return "", DuplicateNamesError
	} else if len(links) < 1 {
		return "", PlacementZoneNotFound
	}
	id := functions.GetResourceID(links[0])
	return EditPZID(id, newName)
}

func EditPZID(id, newName string) (string, error) {
	pzState := PlacementZoneState{
		Name: newName,
	}
	jsonBody, _ := json.Marshal(pzState)
	url := config.URL + functions.CreateResLinkForPlacementZone(id)
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
		if val.PlacementZoneState.Name == pzName {
			links = append(links, key)
		}
	}
	return links
}

func GetPZName(link string) (string, error) {
	url := config.URL + link
	pzs := &PlacementZoneState{}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	err := json.Unmarshal(respBody, pzs)
	functions.CheckJson(err)
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
