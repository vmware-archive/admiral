package resourcePools

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
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

func (rpl *ResourcePoolList) FetchRP() int {
	url := config.URL + "/resources/pools?api_key=resource%20pools"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody := client.ProcessRequest(req)
	err := json.Unmarshal(respBody, rpl)
	functions.CheckJson(err)
	return len(rpl.Documents)
}

func (rpl *ResourcePoolList) Print() {
	if len(rpl.Documents) > 0 {
		count := 1
		fmt.Printf("%-40s %-23s\n", "ID", "NAME")
		for _, val := range rpl.Documents {
			fmt.Printf("%-40s %-23s\n", val.GetID(), val.Name)
			count++
		}
	}
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
	resp, _ := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return "", errors.New("Error occured when removing resource pool.")
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
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return "", errors.New("Error occured when adding resource pool.")
	} else {
		rp := &ResourcePool{}
		err := json.Unmarshal(respBody, rp)
		functions.CheckJson(err)
		return rp.GetID(), nil
	}

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
	resp, _ := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return "", errors.New("Error occured when editing resource pool")
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

func GetRPName(link string) string {
	url := config.URL + link
	rp := &ResourcePool{}
	req, _ := http.NewRequest("GET", url, nil)
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	err := json.Unmarshal(respBody, rp)
	functions.CheckJson(err)
	return rp.Name
}

func GetCustomProperties(id string) map[string]string {
	link := functions.CreateResLinkForRP(id)
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	resp, respBody := client.ProcessRequest(req)
	if resp.StatusCode != 200 {
		return nil
	}
	resPool := &ResourcePool{}
	err := json.Unmarshal(respBody, resPool)
	functions.CheckJson(err)
	return resPool.CustomProperties
}

func GetPublicCustomProperties(id string) map[string]string {
	custProps := GetCustomProperties(id)
	if custProps == nil {
		return nil
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
	return publicCustProps
}

func AddCustomProperties(id string, keys, vals []string) bool {
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
	resp, _ := client.ProcessRequest(req)

	if resp.StatusCode != 200 {
		return false
	}
	return true
}

func RemoveCustomProperties(id string, keys []string) bool {
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
	resp, _ := client.ProcessRequest(req)

	if resp.StatusCode != 200 {
		return false
	}
	return true
}
