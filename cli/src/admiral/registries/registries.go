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

package registries

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"admiral/certificates"
	"admiral/client"
	"admiral/config"
	"admiral/credentials"
	"admiral/utils"
	"admiral/utils/selflink"
)

var (
	RegistryNotFoundError           = errors.New("No registry with that address found.")
	DuplicateNamesError             = errors.New("Registries with duplicate name found, provide ID to remove specific registry.")
	UnexpectedErrorOnRegistryAdd    = errors.New("Error occurred when adding registry.")
	UnexpectedErrorOnRegistryUpdate = errors.New("Error occurred when updating registry.")
	CertNotAcceptedError            = errors.New("Certificate has not been accepted.")
)

type Registry struct {
	Name                 string  `json:"name"`
	Address              string  `json:"address,omitempty"`
	EndpointType         string  `json:"endpointType,omitempty"`
	AuthCredentialsLinks *string `json:"authCredentialsLink,omitempty"`
	Disabled             bool    `json:"disabled,omitempty"`
	DocumentSelfLink     *string `json:"documentSelfLink,omitempty"`
	RegistryState        string  `json:"registryState,omitempty"`
}

func (r *Registry) GetID() string {
	return strings.Replace(*r.DocumentSelfLink, "/config/registries/", "", -1)
}

func (r *Registry) Status() string {
	if r.Disabled {
		return "Disabled"
	} else {
		return "Enabled"
	}
}

type RegistryList struct {
	DocumentLinks []string            `json:"documentLinks"`
	Documents     map[string]Registry `json:"documents"`
}

func (rl *RegistryList) GetCount() int {
	return len(rl.DocumentLinks)
}

func (rl *RegistryList) GetResource(index int) selflink.Identifiable {
	resource := rl.Documents[rl.DocumentLinks[index]]
	return &resource
}

func (rl *RegistryList) FetchRegistries() (int, error) {
	url := config.URL + "/config/registries?documentType=true&expand=true"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, rl)
	utils.CheckJson(err)
	return len(rl.DocumentLinks), nil
}

func (rl *RegistryList) GetOutputString() string {
	var buffer bytes.Buffer
	if len(rl.DocumentLinks) > 0 {
		buffer.WriteString("ID\tNAME\tADDRESS\tSTATUS\n")
		for _, link := range rl.DocumentLinks {
			val := rl.Documents[link]
			output := utils.GetFormattedString(val.GetID(), val.Name, val.Address, val.Status())
			buffer.WriteString(output)
			buffer.WriteString("\n")
		}
	} else {
		buffer.WriteString("No elements found.")
	}
	return strings.TrimSpace(buffer.String())
}

func RemoveRegistry(address string) (string, error) {
	links := getRegLink(address)
	if len(links) < 1 {
		return "", RegistryNotFoundError
	} else if len(links) > 1 {
		return "", DuplicateNamesError
	}
	id := utils.GetResourceID(links[0])
	return RemoveRegistryID(id)
}

func RemoveRegistryID(id string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(RegistryList), utils.REGISTRY)
	utils.CheckIdError(err)
	link := utils.CreateResLinkForRegistry(fullId)
	url := config.URL + link
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil

}

func AddRegistry(regName, addressF, credID, publicCert, privateCert, userName, passWord string, autoAccept bool) (string, error) {
	url := config.URL + "/config/registry-spec"
	var (
		newCredID string
		err       error
		reg       *Registry
	)
	if credID == "" {
		if publicCert != "" && privateCert != "" {
			newCredID, err = credentials.AddByCert(addressF, publicCert, privateCert, nil)
			if err != nil {
				return "", err
			}
		} else if userName != "" && passWord != "" {
			newCredID, err = credentials.AddByUsername(addressF, userName, passWord, nil)
			if err != nil {
				return "", err
			}
		} else {
			newCredID = ""
		}
	} else {
		newCredID, err = selflink.GetFullId(credID, new(credentials.ListCredentials), utils.CREDENTIALS)
		utils.CheckIdError(err)
	}

	reg = &Registry{
		Address:              addressF,
		Name:                 regName,
		EndpointType:         "container.docker.registry",
		AuthCredentialsLinks: nil,
	}

	if newCredID == "" {
		reg.AuthCredentialsLinks = nil
	} else {
		credLink := utils.CreateResLinkForCredentials(newCredID)
		reg.AuthCredentialsLinks = &credLink
	}

	ho := RegistryObj{
		Registry: *reg,
	}

	jsonBody, err := json.Marshal(ho)
	utils.CheckJson(err)

	req, _ := http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
	resp, respBody, respErr := client.ProcessRequest(req)
	if resp.StatusCode == 204 {
		link := resp.Header.Get("Location")
		url = config.URL + link
		req, _ = http.NewRequest("GET", url, nil)
		_, respBody, respErr = client.ProcessRequest(req)
		addedRegistry := &Registry{}
		err = json.Unmarshal(respBody, addedRegistry)
		utils.CheckJson(err)
		return addedRegistry.GetID(), nil
	} else if resp.StatusCode == 200 {
		checkRes := certificates.CheckTrustCert(respBody, autoAccept)
		if checkRes {
			req, _ := http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
			resp, respBody, respErr := client.ProcessRequest(req)
			if respErr != nil {
				return "", respErr
			}
			link := resp.Header.Get("Location")
			url = config.URL + link
			req, _ = http.NewRequest("GET", url, nil)
			_, respBody, respErr = client.ProcessRequest(req)
			if respErr != nil {
				return "", respErr
			}
			addedRegistry := &Registry{}
			err = json.Unmarshal(respBody, addedRegistry)
			utils.CheckJson(err)
			return addedRegistry.GetID(), nil
		}
		return "", CertNotAcceptedError
	} else if respErr != nil {
		return "", respErr
	} else {
		return "", UnexpectedErrorOnRegistryAdd
	}
}

func EditRegistry(address, newAddress, newName, newCred string, autoAccept bool) (string, error) {
	links := getRegLink(address)
	if len(links) < 1 {
		return "", RegistryNotFoundError
	}

	if len(links) > 1 {
		return "", DuplicateNamesError
	}

	id := utils.GetResourceID(links[0])
	return EditRegistryID(id, newAddress, newName, newCred, autoAccept)
}

func EditRegistryID(id, newAddress, newName, newCred string, autoAccept bool) (string, error) {
	fullId, err := selflink.GetFullId(id, new(RegistryList), utils.REGISTRY)
	utils.CheckIdError(err)
	link := utils.CreateResLinkForRegistry(fullId)
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	reg := &Registry{}
	err = json.Unmarshal(respBody, reg)
	utils.CheckJson(err)
	if newAddress != "" {
		reg.Address = newAddress
	}
	if newName != "" {
		reg.Name = newName
	}
	if newCred != "" {
		fullCredId, err := selflink.GetFullId(newCred, new(credentials.ListCredentials), utils.CREDENTIALS)
		utils.CheckIdError(err)
		credLink := utils.CreateResLinkForCredentials(fullCredId)
		reg.AuthCredentialsLinks = &credLink
	}
	url = config.URL + "/config/registry-spec"
	registryObj := &RegistryObj{
		Registry: *reg,
	}
	jsonBody, err := json.Marshal(registryObj)
	utils.CheckJson(err)
	req, _ = http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
	resp, respBody, respErr := client.ProcessRequest(req)
	if resp.StatusCode == 200 {
		checkCert := certificates.CheckTrustCert(respBody, autoAccept)
		if checkCert {
			req, _ = http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
			resp, respBody, respErr = client.ProcessRequest(req)
			if respErr != nil {
				return "", respErr
			}
			link := resp.Header.Get("Location")
			url = config.URL + link
			req, _ = http.NewRequest("GET", url, nil)
			_, respBody, _ = client.ProcessRequest(req)
			reg = &Registry{}
			err = json.Unmarshal(respBody, reg)
			utils.CheckJson(err)
			return reg.GetID(), nil
		}
		return "", errors.New("Error occurred when adding the new certificate.")
	} else if resp.StatusCode == 204 {
		link := resp.Header.Get("Location")
		url = config.URL + link
		req, _ = http.NewRequest("GET", url, nil)
		_, respBody, respErr = client.ProcessRequest(req)
		if respErr != nil {
			return "", respErr
		}
		reg = &Registry{}
		err = json.Unmarshal(respBody, reg)
		utils.CheckJson(err)
		return reg.GetID(), nil
	} else if respErr != nil {
		return "", respErr
	} else {
		return "", UnexpectedErrorOnRegistryUpdate
	}
}

func Disable(address string) (string, error) {
	links := getRegLink(address)
	if len(links) < 1 {
		return "", RegistryNotFoundError
	}

	if len(links) > 1 {
		return "", DuplicateNamesError
	}

	id := utils.GetResourceID(links[0])
	return DisableID(id)
}

func DisableID(id string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(RegistryList), utils.REGISTRY)
	utils.CheckIdError(err)
	link := utils.CreateResLinkForRegistry(fullId)
	rs := &RegistryStatus{
		Disabled:         true,
		DocumentSelfLink: link,
	}
	jsonBody, err := json.Marshal(rs)
	utils.CheckJson(err)
	url := config.URL + link
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr == nil {
		return id, nil
	}
	return "", respErr
}

func Enable(address string) (string, error) {
	links := getRegLink(address)
	if len(links) < 1 {
		return "", RegistryNotFoundError
	}

	if len(links) > 1 {
		return "", DuplicateNamesError
	}

	id := utils.GetResourceID(links[0])
	return EnableID(id)
}

func EnableID(id string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(RegistryList), utils.REGISTRY)
	utils.CheckIdError(err)
	link := utils.CreateResLinkForRegistry(fullId)
	rs := &RegistryStatus{
		Disabled:         false,
		DocumentSelfLink: link,
	}
	jsonBody, err := json.Marshal(rs)
	utils.CheckJson(err)
	url := config.URL + link
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr == nil {
		return id, nil
	}
	return "", respErr
}

func getRegLink(address string) []string {
	rl := &RegistryList{}
	rl.FetchRegistries()
	links := make([]string, 0)
	for _, link := range rl.DocumentLinks {
		val := rl.Documents[link]
		if address == val.Address {
			links = append(links, link)
		}
	}
	return links
}

type RegistryStatus struct {
	Disabled         bool   `json:"disabled"`
	DocumentSelfLink string `json:"documentSelfLink"`
}

type RegistryObj struct {
	Registry Registry `json:"hostState"`
}
