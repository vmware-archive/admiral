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

package hosts

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"regexp"
	"strings"

	"admiral/certificates"
	"admiral/client"
	"admiral/config"
	"admiral/credentials"
	"admiral/placementzones"
	"admiral/properties"
	"admiral/track"
	"admiral/utils"
)

var (
	NewCertNotAddedError          = errors.New("Error occurred when adding the new certificate.")
	AddressNotProvidedError       = errors.New("Host address not provided.")
	PlacementZoneNotProvidedError = errors.New("Placement zone ID not provided.")
)

//Struct part of "Host" struct in order to parse inner data.
type HostProperties struct {
	Containers string `json:"__Containers"`
	Name       string `json:"__Name"`
}

//Struct part of "ListHosts" struct in order to parse inner data.
type Host struct {
	Id               string             `json:"id,omitempty"`
	Address          string             `json:"Address,omitempty"`
	PowerState       string             `json:"powerState,omitempty"`
	CustomProperties map[string]*string `json:"customProperties"`
	ResourcePoolLink string             `json:"resourcePoolLink,omitempty"`
}

func (h *Host) GetResourcePoolID() string {
	return utils.GetResourceID(h.ResourcePoolLink)
}

func (h *Host) GetCredentialsID() string {
	if val, ok := h.CustomProperties["__authCredentialsLink"]; ok && val != nil {
		return utils.GetResourceID(*val)
	}
	return ""
}

//Struct to parse data when getting information about existing hosts.
type HostsList struct {
	TotalCount    int32           `json:"totalCount"`
	Documents     map[string]Host `json:"documents"`
	DocumentLinks []string        `json:"documentLinks"`
}

//Struct used to send data in order to change host's power state.
type HostPatch struct {
	PowerState string `json:"powerState"`
}

//Struct used to send needed data when creating host.
type HostState struct {
	Id               string             `json:"id,omitempty"`
	Address          string             `json:"address"`
	ResourcePoolLink string             `json:"resourcePoolLink"`
	CustomProperties map[string]*string `json:"customProperties"`
}

//Struct used as wrapper of HostState for valid request.
type HostObj struct {
	HostState HostState `json:"hostState"`
}

type HostUpdate struct {
	Credential       credentials.Credentials `json:"credential,omitempty"`
	ResourcePoolLink string                  `json:"resourcePoolLink,omitempty"`
	CustomProperties map[string]*string      `json:"customProperties"`
}

type OperationHost struct {
	Operation     string   `json:"operation"`
	ResourceLinks []string `json:"resourceLinks"`
	ResourceType  string   `json:"resourceType"`
}

//FetchHosts fetches host by query passed as parameter, in case
//all hosts should be fetched, pass empty string as parameter.
//Returns the count of fetched hosts.
func (hl *HostsList) FetchHosts(queryF string) (int, error) {
	var query string
	url := config.URL + "/resources/compute?documentType=true&$count=true&$limit=1000&$orderby=documentSelfLink%20asc&$filter=descriptionLink%20ne%20%27/resources/compute-descriptions/*-parent-compute-desc%27%20and%20customProperties/__computeHost%20eq%20%27*%27%20and%20customProperties/__computeContainerHost%20eq%20%27*%27"

	if strings.TrimSpace(queryF) != "" {
		query = fmt.Sprintf("+and+ALL_FIELDS+eq+'*%s*'", queryF)
		url = url + query
	}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, hl)
	utils.CheckJson(err)
	return int(hl.TotalCount), nil
}

//Print already fetched hosts.
func (hl *HostsList) GetOutputString() string {
	var buffer bytes.Buffer
	buffer.WriteString("ID\tADDRESS\tNAME\tSTATE\tCONTAINERS\tPLACEMENT ZONE")
	buffer.WriteString("\n")
	for _, val := range hl.Documents {
		pzName, _ := placementzones.GetPZName(val.ResourcePoolLink)
		output := utils.GetFormattedString(val.Id, val.Address, *val.CustomProperties["__Name"], val.PowerState,
			*val.CustomProperties["__Containers"], pzName)
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

//AddHost adds host. The function parameters are the address of the host,
//name of the placement zone, name of the deployment policy, name of the credentials.
//The other parameters are in case you want to add new credentials as well. Pass either
//path to files for public and private certificates or username and password. autoaccept is boolean
//which if is true will automatically accept if there is prompt for new certificate, otherwise will prompt
//the user to either agree or disagree. custProps is array of custom properties. Returns the ID of the new
//host that is added and error.
func AddHost(ipF, placementZoneID, deplPolicyID, credID, publicCert, privateCert, userName, passWord string,
	autoAccept bool,
	custProps []string) (string, error) {

	url := config.URL + "/resources/hosts"

	if ok, err := allFlagReadyHost(ipF, placementZoneID); !ok {
		return "", err
	}

	var (
		newCredID string
		err       error
	)

	if credID == "" {
		if publicCert != "" && privateCert != "" {
			newCredID, err = credentials.AddByCert(ipF, publicCert, privateCert, nil)
			if err != nil {
				return "", err
			}
		} else if publicCert != "" && privateCert != "" {
			newCredID, err = credentials.AddByUsername(ipF, userName, passWord, nil)
			if err != nil {
				return "", err
			}
		} else {
			newCredID = ""
		}
	} else {
		newCredID = credID
	}

	pzLink := utils.CreateResLinkForPlacementZone(placementZoneID)

	dpLink := utils.CreateResLinkForDP(deplPolicyID)

	credLink := utils.CreateResLinkForCredentials(newCredID)

	hostProps := properties.ParseCustomProperties(custProps)
	if hostProps == nil {
		hostProps = make(map[string]*string)
	}
	hostProps = properties.MakeHostProperties(credLink, dpLink, hostProps)

	schemeRegex, _ := regexp.Compile("^https?:\\/\\/")
	hostId := schemeRegex.ReplaceAllString(ipF, "")

	host := HostState{
		Address:          ipF,
		ResourcePoolLink: pzLink,
		CustomProperties: hostProps,
		Id:               hostId,
	}

	hostObj := HostObj{
		HostState: host,
	}

	jsonBody, err := json.Marshal(hostObj)
	utils.CheckJson(err)

	req, _ := http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
	resp, respBody, respErr := client.ProcessRequest(req)
	if resp.StatusCode == 200 {
		checkRes := certificates.CheckTrustCert(respBody, autoAccept)
		if checkRes {
			req, _ := http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
			resp, respBody, respErr := client.ProcessRequest(req)
			if resp.StatusCode != 204 {
				credentials.RemoveCredentialsID(newCredID)
				return "", respErr
			}
			link := resp.Header.Get("Location")
			url = config.URL + link
			req, _ = http.NewRequest("GET", url, nil)
			_, respBody, respErr = client.ProcessRequest(req)
			if respErr != nil {
				return "", respErr
			}
			addedHost := &Host{}
			err = json.Unmarshal(respBody, addedHost)
			utils.CheckJson(err)
			return addedHost.Id, nil
		}
		credentials.RemoveCredentialsID(newCredID)
		return "", NewCertNotAddedError
	} else if resp.StatusCode == 204 {
		link := resp.Header.Get("Location")
		url = config.URL + link
		req, _ = http.NewRequest("GET", url, nil)
		_, respBody, respErr := client.ProcessRequest(req)
		if respErr != nil {
			return "", respErr
		}
		addedHost := &Host{}
		err = json.Unmarshal(respBody, addedHost)
		utils.CheckJson(err)
		return addedHost.Id, nil
	}
	return "", respErr
}

//RemoveHost removes host by address passed as parameter, the other parameter is boolean
//to specify if you want to do it as async operation or the program should wait until
//the host is added. Returns the address of the removed host and error = nil, or empty string
//and error != nil.
func RemoveHost(hostAddress string, asyncTask bool) (string, error) {
	url := config.URL + "/requests"

	link := utils.CreateResLinksForHosts(hostAddress)

	jsonRemoveHost := &OperationHost{
		Operation:     "REMOVE_RESOURCE",
		ResourceLinks: []string{link},
		ResourceType:  "CONTAINER_HOST",
	}

	jsonBody, err := json.Marshal(jsonRemoveHost)

	utils.CheckJson(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	taskStatus := &track.OperationResponse{}
	_ = json.Unmarshal(respBody, taskStatus)
	taskStatus.PrintTracerId()
	if !asyncTask {
		_, err = track.Wait(taskStatus.GetTracerId())
	}
	return hostAddress, err
}

func DisableHost(hostAddress string) (string, error) {
	url := config.URL + "/resources/compute/" + hostAddress
	hostp := HostPatch{
		PowerState: "SUSPEND",
	}
	jsonBody, err := json.Marshal(hostp)
	utils.CheckJson(err)

	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return hostAddress, nil
}

func EnableHost(hostAddress string) (string, error) {
	url := config.URL + "/resources/compute/" + hostAddress
	hostp := HostPatch{
		PowerState: "ON",
	}
	jsonBody, err := json.Marshal(hostp)
	utils.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return hostAddress, nil
}

func GetPublicCustomProperties(address string) (map[string]*string, error) {
	props, err := GetCustomProperties(address)
	if props == nil {
		return nil, err
	}
	pubProps := make(map[string]*string)
	for key, val := range props {
		if len(key) > 2 {
			if key[:2] == "__" {
				continue
			}
		}
		pubProps[key] = val
	}
	return pubProps, nil
}

func GetCustomProperties(address string) (map[string]*string, error) {
	link := utils.CreateResLinksForHosts(address)
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	host := &Host{}
	err := json.Unmarshal(respBody, host)
	utils.CheckJson(err)
	return host.CustomProperties, nil
}

func AddCustomProperties(address string, keys, vals []string) error {
	link := utils.CreateResLinksForHosts(address)
	url := config.URL + link
	var lowerLen []string
	if len(keys) > len(vals) {
		lowerLen = vals
	} else {
		lowerLen = keys
	}
	custProps := make(map[string]*string)
	for i := range lowerLen {
		custProps[keys[i]] = &vals[i]
	}
	host := &Host{
		CustomProperties: custProps,
	}
	jsonBody, err := json.Marshal(host)
	utils.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return respErr
	}
	return nil
}

func RemoveCustomProperties(address string, keys []string) error {
	link := utils.CreateResLinksForHosts(address)
	url := config.URL + link
	custProps := make(map[string]*string)
	for i := range keys {
		custProps[keys[i]] = nil
	}
	host := &Host{
		CustomProperties: custProps,
	}
	jsonBody, err := json.Marshal(host)
	utils.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return respErr
	}
	return nil
}

func EditHost(ipF, name, resPoolF, deplPolicyF, credentials string,
	autoAccept bool) (string, error) {
	url := config.URL + "/resources/compute/" + ipF
	props, err := MakeUpdateHostProperties(deplPolicyF, credentials, name)
	if err != nil {
		return "", err
	}

	var (
		rpLink string
	)
	if resPoolF != "" {
		rpLink = utils.GetResourceID(resPoolF)
	}

	newHost := &HostUpdate{
		ResourcePoolLink: rpLink,
		CustomProperties: props,
	}
	jsonBody, err := json.Marshal(newHost)
	utils.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	req.Header.Add("Pragma", "xn-force-index-update")
	_, _, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return "", respErr
	}
	return ipF, nil
}

func allFlagReadyHost(ipF, resPoolF string) (bool, error) {
	if ipF == "" {
		return false, AddressNotProvidedError
	}
	if resPoolF == "" {
		return false, PlacementZoneNotProvidedError
	}
	return true, nil
}

func getHostAddress(name string) string {
	hl := &HostsList{}
	hl.FetchHosts(name)
	if len(hl.DocumentLinks) > 1 {
		return ""
	} else if len(hl.DocumentLinks) < 1 {
		return ""
	}
	return hl.Documents[hl.DocumentLinks[0]].Address
}

func MakeUpdateHostProperties(dp, cred, name string) (map[string]*string, error) {
	props := make(map[string]*string, 0)
	if dp != "" {
		props["__deploymentPolicyLink"] = &dp
	}

	if cred != "" {
		props["__authCredentialsLink"] = &cred
	}

	if name != "" {
		props["__hostAlias"] = &name
	}

	return props, nil
}
