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

package resourcePools

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
	duplMsg  = "Resource pools with duplicate name found, provide ID to remove specific resource pools."
	notFound = "Resource pool not found."
)

type ResourcePool struct {
	Id               string            `json:"id"`
	Name             string            `json:"name"`
	DocumentSelfLink *string           `json:"documentSelfLink"`
	CustomProperties map[string]string `json:"customProperties"`
}

func (rp *ResourcePool) GetID() string {
	return strings.Replace(*rp.DocumentSelfLink, "/resources/pools/", "", -1)
}

type ResourcePoolList struct {
	TotalCount int32                   `json:"totalCount"`
	Documents  map[string]ResourcePool `json:"documents"`
}

type ResourcePoolOperation struct {
	Name             string             `json:"name,omitempty"`
	CustomProperties map[string]*string `json:"customProperties"`
}

func (rpl *ResourcePoolList) FetchRP() (int, error) {
	url := config.URL + "/resources/pools?api_key=resource%20pools"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, rpl)
	functions.CheckJson(err)
	return len(rpl.Documents), nil
}

func (rpl *ResourcePoolList) GetOutputString() string {
	var buffer bytes.Buffer
	if len(rpl.Documents) > 0 {
		buffer.WriteString("ID\tNAME\n")
		for _, val := range rpl.Documents {
			output := functions.GetFormattedString(val.GetID(), val.Name)
			buffer.WriteString(output)
			buffer.WriteString("\n")
		}
	} else {
		buffer.WriteString("No elements found.")
	}
	return strings.TrimSpace(buffer.String())
}

func RemoveRP(rpName string) (string, error) {
	links := GetRPLinks(rpName)
	if len(links) > 1 {
		return "", errors.New(duplMsg)
	} else if len(links) < 1 {
		return "", errors.New(notFound)
	}
	id := functions.GetResourceID(links[0])
	return RemoveRPID(id)
}

func RemoveRPID(id string) (string, error) {
	url := config.URL + functions.CreateResLinkForRP(id)
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil
}

func AddRP(rpName string, custProps []string) (string, error) {
	url := config.URL + "/resources/pools"
	cp := properties.ParseCustomProperties(custProps)
	rpOp := ResourcePoolOperation{
		Name:             rpName,
		CustomProperties: cp,
	}
	jsonBody, _ := json.Marshal(rpOp)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	rp := &ResourcePool{}
	err := json.Unmarshal(respBody, rp)
	functions.CheckJson(err)
	return rp.GetID(), nil

}

func EditRP(rpName, newName string) (string, error) {
	links := GetRPLinks(rpName)
	if len(links) > 1 {
		return "", errors.New(duplMsg)
	} else if len(links) < 1 {
		return "", errors.New(notFound)
	}
	id := functions.GetResourceID(links[0])
	return EditRPID(id, newName)
}

func EditRPID(id, newName string) (string, error) {
	rpOp := ResourcePoolOperation{
		Name: newName,
	}
	jsonBody, _ := json.Marshal(rpOp)
	url := config.URL + functions.CreateResLinkForRP(id)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil
}

func GetRPLinks(rpName string) []string {
	rpl := ResourcePoolList{}
	rpl.FetchRP()
	links := make([]string, 0)
	for key, val := range rpl.Documents {
		if val.Name == rpName {
			links = append(links, key)
		}
	}
	return links
}

func GetRPName(link string) (string, error) {
	url := config.URL + link
	rp := &ResourcePool{}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	err := json.Unmarshal(respBody, rp)
	functions.CheckJson(err)
	return rp.Name, nil
}

func GetCustomProperties(id string) (map[string]string, error) {
	link := functions.CreateResLinkForRP(id)
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	resPool := &ResourcePool{}
	err := json.Unmarshal(respBody, resPool)
	functions.CheckJson(err)
	return resPool.CustomProperties, nil
}

func GetPublicCustomProperties(id string) (map[string]string, error) {
	custProps, err := GetCustomProperties(id)
	if custProps == nil {
		return nil, err
	}
	publicCustProps := make(map[string]string)
	for key, val := range custProps {
		if len(key) > 2 {
			if key[0:2] == "__" {
				continue
			}
		}
		publicCustProps[key] = val
	}
	return publicCustProps, nil
}

func AddCustomProperties(id string, keys, vals []string) error {
	link := functions.CreateResLinkForRP(id)
	url := config.URL + link
	var lowerLen []string
	if len(keys) > len(vals) {
		lowerLen = vals
	} else {
		lowerLen = keys
	}
	custProps := make(map[string]*string)
	for i, _ := range lowerLen {
		custProps[keys[i]] = &vals[i]
	}
	rp := &ResourcePoolOperation{
		CustomProperties: custProps,
	}
	jsonBody, err := json.Marshal(rp)
	functions.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return respErr
	}
	return nil
}

func RemoveCustomProperties(id string, keys []string) error {
	link := functions.CreateResLinkForRP(id)
	url := config.URL + link
	custProps := make(map[string]*string)
	for i := range keys {
		custProps[keys[i]] = nil
	}
	rp := &ResourcePoolOperation{
		CustomProperties: custProps,
	}
	jsonBody, err := json.Marshal(rp)
	functions.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return respErr
	}
	return nil
}
