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

package networks

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"regexp"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/functions"
	"admiral/track"
	"errors"
)

type Network struct {
	Name                string            `json:"name,omitempty"`
	External            bool              `json:"external,omitempty"`
	Driver              string            `json:"driver,omitempty"`
	IPAM                IPAM              `json:"ipam,omitempty"`
	ParentLinks         []string          `json:"parentLinks"`
	ContainerStateLinks []string          `json:"containerStateLinks"`
	PowerState          string            `json:"powerState,omitempty"`
	Id                  string            `json:"id,omitempty"`
	Options             map[string]string `json:"options,omitempty"`
	CustomProperties    map[string]string `json:"customProperties"`
	DocumentSelfLink    string            `json:"documentSelfLink,omitempty"`
}

func (n *Network) SetName(name string) {
	if name == "" {
		fmt.Println("Network name cannot be empty.")
		os.Exit(0)
	}
	n.Name = name
}

func (n *Network) GetID() string {
	return functions.GetResourceID(n.DocumentSelfLink)
}

func (n *Network) GetHostsCount() int {
	return len(n.ParentLinks)
}

func (n *Network) GetContainersCount() int {
	return len(n.ContainerStateLinks)
}

func (n *Network) GetExternalID() string {
	if len(n.Id) <= 14 {
		return n.Id
	}
	return n.Id[0:15]
}

func (n *Network) SetIPAMConfig(subnets, gateways, ipranges []string, ipamDriver string) {
	counts := []int{len(subnets), len(gateways), len(ipranges)}
	biggest := counts[0]
	for i := range counts {
		if counts[i] > biggest {
			biggest = counts[i]
		}
	}

	IPAMConfigs := make([]IPAMConfig, 0)
	for i := 0; i < biggest; i++ {
		IPAMConfig := IPAMConfig{
			Subnet:  subnets[i],
			Gateway: gateways[i],
			IPRange: ipranges[i],
		}
		IPAMConfigs = append(IPAMConfigs, IPAMConfig)
	}
	n.IPAM = IPAM{
		IPAMConfigs: IPAMConfigs,
		Driver:      ipamDriver,
	}
}

func (n *Network) SetOptions(options []string) {
	optMap := make(map[string]string)
	regex, _ := regexp.Compile("\\w+\\s*=\\s*\\w+")
	for _, optPair := range options {
		if !regex.MatchString(optPair) {
			continue
		}
		optPairArr := strings.Split(optPair, "=")
		optMap[optPairArr[0]] = optPairArr[1]
	}
	n.Options = optMap
}

func (n *Network) SetCustomProperties(customProperties []string) {
	customProps := make(map[string]string)
	regex, _ := regexp.Compile("\\w+\\s*=\\s*\\w+")
	for _, optPair := range customProperties {
		if !regex.MatchString(optPair) {
			continue
		}
		customPropPair := strings.Split(optPair, "=")
		customProps[customPropPair[0]] = customPropPair[1]
	}
	n.CustomProperties = customProps
}

func (n *Network) String() string {
	jsonBody, err := json.MarshalIndent(n, "", "    ")
	functions.CheckJson(err)
	return string(jsonBody)
}

type IPAM struct {
	Driver      string       `json:"driver,omitempty"`
	IPAMConfigs []IPAMConfig `json:"config"`
}

type IPAMConfig struct {
	Subnet       string            `json:"subnet,omitempty"`
	Gateway      string            `json:"gateway,omitempty"`
	IPRange      string            `json:"ipRange,omitempty"`
	AuxAddresses map[string]string `json:"auxAddresses,omitempty"`
}

type NetworkList struct {
	DocumentLinks []string           `json:"documentLinks"`
	Documents     map[string]Network `json:"documents"`
}

type NetworkDescription struct {
	DocumentSelfLink string `json:"documentSelfLink"`
}

type NetworkOperation struct {
	CustomProperties        map[string]string `json:"customProperties,omitempty"`
	ResourceDescriptionLink string            `json:"resourceDescriptionLink"`
	ResourceType            string            `json:"resourceType"`
	Operation               string            `json:"operation,omitempty"`
	ResourceLinks           []string          `json:"resourceLinks,omitempty"`
}

func (no *NetworkOperation) SetCustomProperties(hosts []string) {
	no.CustomProperties = make(map[string]string, 0)
	no.CustomProperties["__containerHostId"] = strings.Join(hosts, ",")
}

func (nl *NetworkList) FetchNetworks() (int, error) {
	url := config.URL + "/resources/container-networks?expand"
	req, err := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err = json.Unmarshal(respBody, nl)
	functions.CheckJson(err)
	return len(nl.DocumentLinks), nil
}

func (nl *NetworkList) GetOutputString() string {
	var buffer bytes.Buffer
	if len(nl.DocumentLinks) < 1 {
		buffer.WriteString("No elements found.")
		return buffer.String()
	}
	buffer.WriteString("ID\tNAME\tNETWORK DRIVER\tCONTAINERS\tHOSTS\tPOWER STATE\tEXTERNAL ID\n")
	for _, link := range nl.DocumentLinks {
		val := nl.Documents[link]
		output := functions.GetFormattedString(val.GetID(), val.Name, val.Driver,
			val.GetContainersCount(), val.GetHostsCount(), val.PowerState, val.GetExternalID())
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

func RemoveNetwork(ids []string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	links := functions.CreateResLinksForNetwork(ids)
	no := &NetworkOperation{
		Operation:     "Network.Delete",
		ResourceType:  "NETWORK",
		ResourceLinks: links,
	}
	jsonBody, err := json.Marshal(no)
	functions.CheckJson(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return nil, respErr
	}

	taskStatus := &track.OperationResponse{}
	err = json.Unmarshal(respBody, taskStatus)
	functions.CheckJson(err)
	taskStatus.PrintTracerId()
	resLinks := make([]string, 0)
	if !asyncTask {
		resLinks, err = track.Wait(taskStatus.GetTracerId())
	} else {
		resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
		if len(resLinks) < 1 {
			return ids, err
		}
	}
	resourcesIDs := functions.GetResourceIDs(resLinks)
	return resourcesIDs, err

}

func InspectNetwork(id string) (string, error) {
	links := functions.CreateResLinksForNetwork([]string{id})
	url := config.URL + links[0]
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	network := &Network{}
	err := json.Unmarshal(respBody, network)
	functions.CheckJson(err)
	return network.String(), nil
}

func CreateNetwork(name, networkDriver, ipamDriver string,
	gateways, subnets, ipranges, customProperties,
	hosts []string, asyncTask bool) (string, error) {
	if len(hosts) < 1 {
		return "", errors.New("Host address is not provided.")
	}
	network := &Network{
		Name:     name,
		External: false,
		Driver:   networkDriver,
	}
	network.SetCustomProperties(customProperties)
	network.SetIPAMConfig(subnets, gateways, ipranges, ipamDriver)

	url := config.URL + "/resources/container-network-descriptions"
	jsonBody, err := json.Marshal(network)
	functions.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	nd := &NetworkDescription{}
	err = json.Unmarshal(respBody, nd)
	functions.CheckJson(err)
	networkLink := nd.DocumentSelfLink

	no := &NetworkOperation{
		ResourceDescriptionLink: networkLink,
		ResourceType:            "NETWORK",
	}
	no.SetCustomProperties(hosts)
	jsonBody, err = json.Marshal(no)
	url = config.URL + "/requests"
	req, _ = http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr = client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}

	taskStatus := &track.OperationResponse{}
	err = json.Unmarshal(respBody, taskStatus)
	functions.CheckJson(err)
	taskStatus.PrintTracerId()
	resLinks := make([]string, 0)
	if !asyncTask {
		resLinks, err = track.Wait(taskStatus.GetTracerId())
	}

	if len(resLinks) < 1 {
		return "", nil
	}

	return functions.GetResourceID(resLinks[0]), err
}
