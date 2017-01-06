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
	"admiral/tags"
	"admiral/track"
	"admiral/utils"
	"admiral/utils/selflink"
	"admiral/utils/urlutils"
	"strconv"
)

var (
	NewCertNotAddedError          = errors.New("Error occurred when adding the new certificate.")
	AddressNotProvidedError       = errors.New("Host address not provided.")
	PlacementZoneNotProvidedError = errors.New("Placement zone ID not provided.")
)

//Struct part of "ListHosts" struct in order to parse inner data.
type Host struct {
	Id               string             `json:"id,omitempty"`
	Address          string             `json:"address,omitempty"`
	PowerState       string             `json:"powerState,omitempty"`
	CustomProperties map[string]*string `json:"customProperties,omitempty"`
	ResourcePoolLink string             `json:"resourcePoolLink,omitempty"`
	TagLinks         []string           `json:"tagLinks,omitempty"`
	Name             string             `json:"name,omitempty"`

	CreationTimeMicros           interface{} `json:"creationTimeMicros,omitempty"`
	DescriptionLink              string      `json:"descriptionLink,omitempty"`
	DocumentSelfLink             string      `json:"documentSelfLink,omitempty"`
	DocumentVersion              int         `json:"documentVersion,omitempty"`
	DocumentEpoch                int         `json:"documentEpoch,omitempty"`
	DocumentKind                 string      `json:"documentKind,omitempty"`
	DocumentUpdateTimeMicros     interface{} `json:"documentUpdateTimeMicros,omitempty"`
	DocumentUpdateAction         string      `json:"documentUpdateAction,omitempty"`
	DocumentExpirationTimeMicros interface{} `json:"documentExpirationTimeMicros,omitempty"`
	DocumentOwner                string      `json:"documentOwner,omitempty"`
	DocumentAuthPrincipalLink    string      `json:"documentAuthPrincipalLink,omitempty"`
}

func (h *Host) GetName() string {
	if val, ok := h.CustomProperties["__hostAlias"]; ok && val != nil && strings.TrimSpace(*val) != "" {
		return *val
	}

	if strings.Contains(h.Id, h.Address) {
		if val, ok := h.CustomProperties["__Name"]; ok && val != nil && strings.TrimSpace(*val) != "" {
			return *val
		}
	}
	return h.GetID()
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
	return utils.GetResourceID(h.DocumentSelfLink)
}

func (h *Host) SetCustomProperties(ipF, deplPolicyID, name, credID,
	publicCert, privateCert, userName, passWord string,
	custProps []string) (bool, error) {

	if h.CustomProperties == nil {
		h.CustomProperties = make(map[string]*string, 0)
	}

	var (
		newCredID        string
		err              error
		credLink, dpLink string
		isCredNew        bool
	)
	if credID == "" {
		if publicCert != "" && privateCert != "" {
			newCredID, err = credentials.AddByCert(ipF, publicCert, privateCert, nil)
			if err != nil {
				return isCredNew, err
			}
			isCredNew = true
		} else if publicCert != "" && privateCert != "" {
			newCredID, err = credentials.AddByUsername(ipF, userName, passWord, nil)
			if err != nil {
				return isCredNew, err
			}
			isCredNew = true
		} else {
			newCredID = ""
		}
	} else {
		newCredID, err = selflink.GetFullId(credID, new(credentials.CredentialsList), utils.CREDENTIALS)
		utils.CheckBlockingError(err)
	}

	credLink = utils.CreateResLinkForCredentials(newCredID)

	if deplPolicyID != "" {
		fullDpId, err := selflink.GetFullId(deplPolicyID, new(deplPolicy.DeploymentPolicyList), utils.DEPLOYMENT_POLICY)
		utils.CheckBlockingError(err)
		dpLink = utils.CreateResLinkForDeploymentPolicies(fullDpId)
	}

	properties.ParseCustomProperties(custProps, h.CustomProperties)
	properties.MakeHostProperties(credLink, dpLink, name, h.CustomProperties)

	return isCredNew, nil
}

func (h *Host) SetResourcePoolLink(placementZoneID string) {
	if placementZoneID == "" {
		return
	}
	fullPzId, err := selflink.GetFullId(placementZoneID, new(placementzones.PlacementZoneList), utils.PLACEMENT_ZONE)
	utils.CheckBlockingError(err)
	pzLink := utils.CreateResLinkForResourcePool(fullPzId)
	h.ResourcePoolLink = pzLink

}

func (h *Host) SetAddress(address string) {
	h.Address = address
}

func (h *Host) SetId(address string) {
	schemeRegex, _ := regexp.Compile("^https?:\\/\\/")
	hostId := schemeRegex.ReplaceAllString(address, "")
	h.Id = hostId
}

func (h *Host) AddTagLinks(tagsInput []string) error {
	if h.TagLinks == nil {
		h.TagLinks = make([]string, 0)
	}
	for _, ti := range tagsInput {
		tagId, err := tags.GetTagIdByEqualKeyVals(ti, true)
		if err != nil {
			return err
		}
		tagLink := utils.CreateResLinkForTag(tagId)
		if tagLink != "" && !h.containsTagLink(tagLink) {
			h.TagLinks = append(h.TagLinks, tagLink)
		}
	}
	return nil
}

func (h *Host) RemoveTagLinks(tagsInput []string) error {
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
		for i := 0; i < len(h.TagLinks); i++ {
			if tagToRemove == h.TagLinks[i] {
				h.TagLinks = append(h.TagLinks[:i], h.TagLinks[i+1:]...)
				i--
			}
		}
	}

	return nil
}

func (h *Host) SetTagLinks(tagLinks []string) {
	h.TagLinks = tagLinks
}

func (h *Host) containsTagLink(tagLink string) bool {
	for _, tl := range h.TagLinks {
		if tl == tagLink {
			return true
		}
	}
	return false
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

func (hl *HostsList) Renew() {
	*hl = HostsList{}
}

//Struct used to send data in order to change host's power state.
type HostPatch struct {
	PowerState string `json:"powerState"`
}

//Struct used as wrapper of HostState for valid request.
type HostObj struct {
	HostState         Host `json:"hostState"`
	IsUpdateOperation bool `json:"isUpdateOperation"`
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
	cqm := urlutils.GetCommonQueryMap()
	cqm["$orderby"] = "documentSelfLink%20asc"
	cqm["$filter"] = "descriptionLink%20ne%20%27/resources/compute-descriptions/*-parent-compute-desc%27%20and%20customProperties/__computeHost%20eq%20%27*%27%20and%20customProperties/__computeContainerHost%20eq%20%27*%27"
	if strings.TrimSpace(queryF) != "" {
		query := fmt.Sprintf("+and+ALL_FIELDS+eq+'*%s*'", queryF)
		cqm["$filter"] = cqm["$filter"].(string) + query
	}
	url := urlutils.BuildUrl(urlutils.Compute, cqm, true)
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
	buffer.WriteString("ID\tADDRESS\tNAME\tSTATE\tCONTAINERS\tPLACEMENT ZONE\tTAGS")
	buffer.WriteString("\n")
	for _, val := range hl.Documents {
		pzName, _ := placementzones.GetPZName(val.ResourcePoolLink)
		tagsStr := tags.TagsToString(val.TagLinks)
		output := utils.GetTabSeparatedString(val.Id, val.Address, val.GetName(), val.PowerState,
			val.GetContainersCount(), pzName, tagsStr)
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
	custProps, tags []string) (string, error) {

	url := urlutils.BuildUrl(urlutils.Host, nil, true)

	if ok, err := allFlagReadyHost(ipF); !ok {
		return "", err
	}

	hostName := ""
	host := &Host{}
	host.SetAddress(ipF)
	host.SetId(ipF)
	host.SetResourcePoolLink(placementZoneID)
	isNewCred, err := host.SetCustomProperties(ipF, deplPolicyID, hostName, credID, publicCert, privateCert, userName, passWord, custProps)
	if err != nil {
		removeNewCredentials(host.GetCredentialsID(), isNewCred)
		return "", err
	}

	err = host.AddTagLinks(tags)
	if err != nil {
		removeNewCredentials(host.GetCredentialsID(), isNewCred)
		return "", err
	}

	hostObj := HostObj{
		HostState: *host,
	}

	jsonBody, err := json.Marshal(hostObj)
	utils.CheckBlockingError(err)

	req, _ := http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
	resp, respBody, respErr := client.ProcessRequest(req)

	if respErr != nil {
		removeNewCredentials(host.GetCredentialsID(), isNewCred)
		return "", respErr
	}

	if resp.StatusCode == 200 {
		checkRes := certificates.CheckTrustCert(respBody, autoAccept)
		if checkRes {
			req, _ := http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
			resp, respBody, respErr := client.ProcessRequest(req)
			if resp.StatusCode != 204 {
				removeNewCredentials(host.GetCredentialsID(), isNewCred)
				return "", respErr
			}
			link := resp.Header.Get("Location")
			url = config.URL + link
			req, _ = http.NewRequest("GET", url, nil)
			_, respBody, respErr = client.ProcessRequest(req)
			if respErr != nil {
				removeNewCredentials(host.GetCredentialsID(), isNewCred)
				return "", respErr
			}
			addedHost := &Host{}
			err = json.Unmarshal(respBody, addedHost)
			utils.CheckBlockingError(err)
			return addedHost.Id, nil
		}
		removeNewCredentials(host.GetCredentialsID(), isNewCred)
		return "", NewCertNotAddedError
	} else if resp.StatusCode == 204 {
		link := resp.Header.Get("Location")
		url = config.URL + link
		req, _ = http.NewRequest("GET", url, nil)
		_, respBody, respErr := client.ProcessRequest(req)
		if respErr != nil {
			removeNewCredentials(host.GetCredentialsID(), isNewCred)
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
	url := urlutils.BuildUrl(urlutils.RequestBrokerService, nil, true)
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
		resLinks, err := track.StartWaitingFromResponseBody(respBody)
		return strings.Join(resLinks, " "), err
	}
	track.PrintTaskIdFromResponseBody(respBody)
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
	return fullId, nil
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
	return fullId, nil
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
	autoAccept bool,
	tagsToAdd, tagsToRemove []string) (string, error) {
	url := urlutils.BuildUrl(urlutils.Host, nil, true)

	oldHost, err := getHost(id)
	if err != nil {
		return "", err
	}

	oldHost.SetCustomProperties("", deplPolicyF, name, credId, "", "", "", "", nil)
	oldHost.SetResourcePoolLink(placementZoneId)
	err = oldHost.AddTagLinks(tagsToAdd)
	if err != nil {
		return "", err
	}
	err = oldHost.RemoveTagLinks(tagsToRemove)
	if err != nil {
		return "", err
	}

	hostObj := &HostObj{
		HostState:         *oldHost,
		IsUpdateOperation: true,
	}

	jsonBody, err := json.Marshal(hostObj)
	utils.CheckBlockingError(err)
	req, _ := http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))

	req.Header.Add("Pragma", "xn-force-index-update")
	_, _, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return "", respErr
	}
	return oldHost.GetID(), nil
}

func allFlagReadyHost(ipF string) (bool, error) {
	if ipF == "" {
		return false, AddressNotProvidedError
	}
	return true, nil
}

func removeNewCredentials(credID string, isNewCred bool) {
	if !isNewCred {
		return
	}
	credentials.RemoveCredentialsID(credID)
}

func getHost(id string) (*Host, error) {
	fullId, err := selflink.GetFullId(id, new(HostsList), utils.HOST)
	utils.CheckBlockingError(err)
	link := utils.CreateResLinksForHosts(fullId)
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	host := &Host{}
	err = json.Unmarshal(respBody, host)
	utils.CheckBlockingError(err)
	return host, nil
}
