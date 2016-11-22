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
	"admiral/deplPolicy"
	"admiral/placementzones"
	"admiral/properties"
	"admiral/track"
	"admiral/utils"
	"admiral/utils/selflink"
	"strconv"
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

func (h *Host) GetName() string {
	if val, ok := h.CustomProperties["__hostAlias"]; !ok || val == nil || strings.TrimSpace(*val) == "" {
		return *h.CustomProperties["__Name"]
	}
	return *h.CustomProperties["__hostAlias"]
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

func (h *Host) GetContainersCount() int {
	count, _ := strconv.Atoi(*h.CustomProperties["__Containers"])
	//Returning count - 1, because we exclude the system container.
	return count - 1
}

func (h *Host) GetID() string {
	return h.Id
}

//Struct to parse data when getting information about existing hosts.
type HostsList struct {
	TotalCount    int32           `json:"totalCount"`
	Documents     map[string]Host `json:"documents"`
	DocumentLinks []string        `json:"documentLinks"`
}

func (hl *HostsList) GetCount() int {
	return len(hl.DocumentLinks)
}

func (hl *HostsList) GetResource(index int) selflink.Identifiable {
	resource := hl.Documents[hl.DocumentLinks[index]]
	return &resource
}

//Struct used to send data in order to change host's power state.
type HostPatch struct {
	PowerState string `json:"powerState"`
}

//Struct used to send needed data when creating host.
type HostState struct {
	Id               string             `json:"id,omitempty"`
	Address          string             `json:"address"`
	ResourcePoolLink string             `json:"resourcePoolLink,omitempty"`
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
	utils.CheckBlockingError(err)
	return int(hl.TotalCount - 1), nil
}

//Print already fetched hosts.
func (hl *HostsList) GetOutputString() string {
	if hl.GetCount() < 1 {
		return utils.NoElementsFoundMessage
	}
	var buffer bytes.Buffer
	buffer.WriteString("ID\tADDRESS\tNAME\tSTATE\tCONTAINERS\tPLACEMENT ZONE")
	buffer.WriteString("\n")
	for _, val := range hl.Documents {
		pzName, _ := placementzones.GetPZName(val.ResourcePoolLink)
		output := utils.GetTabSeparatedString(val.Id, val.Address, val.GetName(), val.PowerState,
			val.GetContainersCount(), pzName)
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

	if ok, err := allFlagReadyHost(ipF); !ok {
		return "", err
	}

	var (
		newCredID string
		dpLink    string
		fullDpId  string
		pzLink    string
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
		newCredID, err = selflink.GetFullId(credID, new(credentials.ListCredentials), utils.CREDENTIALS)
		utils.CheckBlockingError(err)
	}

	if placementZoneID != "" {
		fullPzId, err := selflink.GetFullId(placementZoneID, new(placementzones.PlacementZoneList), utils.PLACEMENT_ZONE)
		utils.CheckBlockingError(err)
		pzLink = utils.CreateResLinkForPlacementZone(fullPzId)
	}

	if deplPolicyID != "" {
		fullDpId, err = selflink.GetFullId(deplPolicyID, new(deplPolicy.DeploymentPolicyList), utils.DEPLOYMENT_POLICY)
		utils.CheckBlockingError(err)
		dpLink = utils.CreateResLinkForDP(fullDpId)
	}

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
	utils.CheckBlockingError(err)

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
			utils.CheckBlockingError(err)
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
		utils.CheckBlockingError(err)
		return addedHost.Id, nil
	}
	return "", respErr
}

//RemoveHost removes host by address passed as parameter, the other parameter is boolean
//to specify if you want to do it as async operation or the program should wait until
//the host is added. Returns the address of the removed host and error = nil, or empty string
//and error != nil.
func RemoveHost(id string, asyncTask bool) (string, error) {
	url := config.URL + "/requests"
	fullId, err := selflink.GetFullId(id, new(HostsList), utils.HOST)
	utils.CheckBlockingError(err)
	link := utils.CreateResLinksForHosts(fullId)

	jsonRemoveHost := &OperationHost{
		Operation:     "REMOVE_RESOURCE",
		ResourceLinks: []string{link},
		ResourceType:  "CONTAINER_HOST",
	}

	jsonBody, err := json.Marshal(jsonRemoveHost)

	utils.CheckBlockingError(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	if !asyncTask {
		resLinks, err := track.StartWaitingFromResponse(respBody)
		return strings.Join(resLinks, ", "), err
	}
	return "", nil
}

func DisableHost(id string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(HostsList), utils.HOST)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinksForHosts(fullId)
	hostp := HostPatch{
		PowerState: "SUSPEND",
	}
	jsonBody, err := json.Marshal(hostp)
	utils.CheckBlockingError(err)

	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil
}

func EnableHost(id string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(HostsList), utils.HOST)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinksForHosts(fullId)
	hostp := HostPatch{
		PowerState: "ON",
	}
	jsonBody, err := json.Marshal(hostp)
	utils.CheckBlockingError(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil
}

func GetPublicCustomProperties(id string) (map[string]*string, error) {
	props, err := GetCustomProperties(id)
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

func GetCustomProperties(id string) (map[string]*string, error) {
	fullId, err := selflink.GetFullId(id, new(HostsList), utils.HOST)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinksForHosts(fullId)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	host := &Host{}
	err = json.Unmarshal(respBody, host)
	utils.CheckBlockingError(err)
	return host.CustomProperties, nil
}

func AddCustomProperties(id string, keys, vals []string) error {
	fullId, err := selflink.GetFullId(id, new(HostsList), utils.HOST)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinksForHosts(fullId)
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
	utils.CheckBlockingError(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return respErr
	}
	return nil
}

func RemoveCustomProperties(id string, keys []string) error {
	fullId, err := selflink.GetFullId(id, new(HostsList), utils.HOST)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinksForHosts(fullId)
	custProps := make(map[string]*string)
	for i := range keys {
		custProps[keys[i]] = nil
	}
	host := &Host{
		CustomProperties: custProps,
	}
	jsonBody, err := json.Marshal(host)
	utils.CheckBlockingError(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return respErr
	}
	return nil
}

func EditHost(id, name, placementZoneId, deplPolicyF, credId string,
	autoAccept bool) (string, error) {
	fullId, err := selflink.GetFullId(id, new(HostsList), utils.HOST)
	utils.CheckBlockingError(err)
	url := config.URL + utils.CreateResLinksForHosts(fullId)

	var (
		fullDpId   string
		fullCredId string
		fullPzId   string
		pzLink     string
	)
	if deplPolicyF != "" {
		fullDpId, err = selflink.GetFullId(deplPolicyF, new(deplPolicy.DeploymentPolicyList), utils.DEPLOYMENT_POLICY)
		utils.CheckBlockingError(err)
	}
	if credId != "" {
		fullCredId, err = selflink.GetFullId(credId, new(credentials.ListCredentials), utils.CREDENTIALS)
		utils.CheckBlockingError(err)
	}
	if placementZoneId != "" {
		fullPzId, err = selflink.GetFullId(placementZoneId, new(placementzones.PlacementZoneList), utils.PLACEMENT_ZONE)
		utils.CheckBlockingError(err)
		pzLink = utils.CreateResLinkForPlacementZone(fullPzId)
	}

	props := MakeUpdateHostProperties(fullDpId, fullCredId, name)
	newHost := &HostUpdate{
		ResourcePoolLink: pzLink,
		CustomProperties: props,
	}
	jsonBody, err := json.Marshal(newHost)
	utils.CheckBlockingError(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	req.Header.Add("Pragma", "xn-force-index-update")
	_, _, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return "", respErr
	}
	return id, nil
}

func allFlagReadyHost(ipF string) (bool, error) {
	if ipF == "" {
		return false, AddressNotProvidedError
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

func MakeUpdateHostProperties(dp, cred, name string) map[string]*string {
	props := make(map[string]*string, 0)
	if dp != "" {
		dpLink := utils.CreateResLinkForDP(dp)
		props["__deploymentPolicyLink"] = &dpLink
	}

	if cred != "" {
		credLink := utils.CreateResLinkForCredentials(cred)
		props["__authCredentialsLink"] = &credLink
	}

	if name != "" {
		props["__hostAlias"] = &name
	}
	return props
}
