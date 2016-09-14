package registries

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"

	"admiral/certificates"
	"admiral/client"
	"admiral/config"
	"admiral/credentials"
	"admiral/functions"
)

var (
	duplMsg     = "Registries with duplicate name found, provide ID to remove specific registry."
	notFoundMsg = "No registry with that address found."
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

func (rl *RegistryList) FetchRegistries() int {
	url := config.URL + "/config/registries?documentType=true&expand=true"
	req, _ := http.NewRequest("GET", url, nil)
	resp, respBody := client.ProcessRequest(req)
	err := json.Unmarshal(respBody, rl)
	functions.CheckJson(err)
	defer resp.Body.Close()
	return len(rl.DocumentLinks)
}

func (rl *RegistryList) Print() {
	if len(rl.DocumentLinks) > 0 {
		fmt.Printf("%-40s %-20s %-40s %-15s\n", "ID", "NAME", "ADDRESS", "STATUS")
		for _, link := range rl.DocumentLinks {
			val := rl.Documents[link]
			fmt.Printf("%-40s %-20s %-40s %-15s\n", val.GetID(), val.Name, val.Address, val.Status())
		}
	} else {
		fmt.Println("n/a")
	}
}

func RemoveRegistry(address string) (string, error) {
	links := getRegLink(address)
	if len(links) < 1 {
		return "", errors.New(notFoundMsg)
	} else if len(links) > 1 {
		return "", errors.New(duplMsg)
	}
	id := functions.GetResourceID(links[0])
	return RemoveRegistryID(id)
}

func RemoveRegistryID(id string) (string, error) {
	link := functions.CreateResLinkForRegistry(id)
	url := config.URL + link
	req, _ := http.NewRequest("DELETE", url, nil)
	resp, _ := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode == 200 {
		return id, nil
	}
	return id, errors.New("Error occured when removing registry.")

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
		} else if publicCert != "" && privateCert != "" {
			newCredID, err = credentials.AddByUsername(addressF, userName, passWord, nil)
			if err != nil {
				return "", err
			}
		} else {
			newCredID = ""
		}
	} else {
		newCredID = credID
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
		credLink := functions.CreateResLinkForCredentials(newCredID)
		reg.AuthCredentialsLinks = &credLink
	}

	ho := RegistryObj{
		Registry: *reg,
	}

	jsonBody, err := json.Marshal(ho)
	functions.CheckJson(err)

	req, _ := http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	if resp.StatusCode == 204 {
		link := resp.Header.Get("Location")
		url = config.URL + link
		req, _ = http.NewRequest("GET", url, nil)
		_, respBody = client.ProcessRequest(req)
		addedRegistry := &Registry{}
		err = json.Unmarshal(respBody, addedRegistry)
		functions.CheckJson(err)
		return addedRegistry.GetID(), nil
	} else if resp.StatusCode == 200 {
		checkRes := certificates.CheckTrustCert(respBody, autoAccept)
		if checkRes {
			req, _ := http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
			resp, respBody := client.ProcessRequest(req)
			if resp.StatusCode != 204 {
				return "", errors.New("Error occured when adding registry")
			}
			link := resp.Header.Get("Location")
			url = config.URL + link
			req, _ = http.NewRequest("GET", url, nil)
			_, respBody = client.ProcessRequest(req)
			addedRegistry := &Registry{}
			err = json.Unmarshal(respBody, addedRegistry)
			functions.CheckJson(err)
			return addedRegistry.GetID(), nil
		}
		return "", errors.New("Certificate has not been accepted.")
	} else {
		return "", errors.New("Error occured when adding registry.")
	}
}

func EditRegistry(address, newAddress, newName, newCred string, autoAccept bool) (string, error) {
	links := getRegLink(address)
	if len(links) < 1 {
		return "", errors.New(notFoundMsg)
	}

	if len(links) > 1 {
		return "", errors.New(duplMsg)
	}

	id := functions.GetResourceID(links[0])
	return EditRegistryID(id, newAddress, newName, newCred, autoAccept)
}

func EditRegistryID(id, newAddress, newName, newCred string, autoAccept bool) (string, error) {
	link := functions.CreateResLinkForRegistry(id)
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody := client.ProcessRequest(req)
	reg := &Registry{}
	err := json.Unmarshal(respBody, reg)
	functions.CheckJson(err)
	if newAddress != "" {
		reg.Address = newAddress
	}
	if newName != "" {
		reg.Name = newName
	}
	if newCred != "" {
		credLinks := credentials.GetCredentialsLinks(newCred)
		if len(credLinks) < 1 {
			return "", errors.New("Credentials not found.")
		} else if len(credLinks) > 1 {
			return "", errors.New("Credentials with duplicate name found, fix it manually and then proceed.")
		}
		credLink := credLinks[0]
		reg.AuthCredentialsLinks = &credLink
	}
	url = config.URL + "/config/registry-spec"
	registryObj := &RegistryObj{
		Registry: *reg,
	}
	jsonBody, err := json.Marshal(registryObj)
	functions.CheckJson(err)
	req, _ = http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	if resp.StatusCode == 200 {
		checkCert := certificates.CheckTrustCert(respBody, autoAccept)
		if checkCert {
			req, _ = http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
			resp, respBody = client.ProcessRequest(req)
			if resp.StatusCode != 204 {
				return "", errors.New("Error occured when updating registry.")
			}
			link := resp.Header.Get("Location")
			url = config.URL + link
			req, _ = http.NewRequest("GET", url, nil)
			_, respBody = client.ProcessRequest(req)
			reg = &Registry{}
			err = json.Unmarshal(respBody, reg)
			functions.CheckJson(err)
			return reg.GetID(), nil
		}
		return "", errors.New("Error occured when adding the new certificate.")
	} else if resp.StatusCode == 204 {
		link := resp.Header.Get("Location")
		url = config.URL + link
		req, _ = http.NewRequest("GET", url, nil)
		_, respBody = client.ProcessRequest(req)
		reg = &Registry{}
		err = json.Unmarshal(respBody, reg)
		functions.CheckJson(err)
		return reg.GetID(), nil
	} else {
		return "", errors.New("Error occured when updating registry.")
	}
}

func Disable(address string) (string, error) {
	links := getRegLink(address)
	if len(links) < 1 {
		return "", errors.New(notFoundMsg)
	}

	if len(links) > 1 {
		return "", errors.New(duplMsg)
	}

	id := functions.GetResourceID(links[0])
	return DisableID(id)
}

func DisableID(id string) (string, error) {
	link := functions.CreateResLinkForRegistry(id)
	rs := &RegistryStatus{
		Disabled:         true,
		DocumentSelfLink: link,
	}
	jsonBody, err := json.Marshal(rs)
	functions.CheckJson(err)
	url := config.URL + link
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	resp, _ := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode == 200 {
		return id, nil
	} else {
		return id, errors.New("Error occured when disabling registry.")
	}
}

func Enable(address string) (string, error) {
	links := getRegLink(address)
	if len(links) < 1 {
		return "", errors.New(notFoundMsg)
	}

	if len(links) > 1 {
		return "", errors.New(duplMsg)
	}

	id := functions.GetResourceID(links[0])
	return EnableID(id)
}

func EnableID(id string) (string, error) {
	link := functions.CreateResLinkForRegistry(id)
	rs := &RegistryStatus{
		Disabled:         false,
		DocumentSelfLink: link,
	}
	jsonBody, err := json.Marshal(rs)
	functions.CheckJson(err)
	url := config.URL + link
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	resp, _ := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode == 200 {
		return id, nil
	} else {
		return id, errors.New("Error occured when enabling registry.")
	}
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
