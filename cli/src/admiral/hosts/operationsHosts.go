package hosts

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
	"admiral/containers"
	"admiral/credentials"
	"admiral/deplPolicy"
	"admiral/functions"
	"admiral/properties"
	"admiral/resourcePools"
	"admiral/track"
)

//FetchHosts fetches host by query passed as parameter, in case
//all hosts should be fetched, pass empty string as parameter.
//Returns the count of fetched hosts.
func (hl *HostsList) FetchHosts(queryF string) int {
	var query string
	url := config.URL + "/resources/compute?documentType=true&$count=true&$limit=1000&$orderby=documentSelfLink%20asc&$filter=descriptionLink%20ne%20%27/resources/compute-descriptions/*-parent-compute-desc%27%20and%20customProperties/__computeHost%20eq%20%27*%27%20and%20customProperties/__computeContainerHost%20eq%20%27*%27"

	if strings.TrimSpace(queryF) != "" {
		query = fmt.Sprintf("+and+ALL_FIELDS+eq+'*%s*'", queryF)
		url = url + query
	}
	req, _ := http.NewRequest("GET", url, nil)
	resp, respBody := client.ProcessRequest(req)
	err := json.Unmarshal(respBody, hl)
	functions.CheckJson(err)
	defer resp.Body.Close()
	return int(hl.TotalCount)
}

//Print already fetched hosts.
func (hl *HostsList) Print() {
	count := 1
	fmt.Printf("%-22s %-22s %-8s %-3s\n", "ADDRESS", "NAME", "STATE", "CONTAINERS COUNT")
	for _, val := range hl.Documents {
		fmt.Printf("%-22s %-22s %-8s %-3s\n", val.Address, *val.CustomProperties["__Name"], val.PowerState, *val.CustomProperties["__Containers"])
		count++
	}
}

//AddHost adds host. The function parameters are the address of the host,
//name of the resource pool, name of the deployment policy, name of the credentials.
//The other parameters are in case you want to add new credentials as well. Pass either
//path to files for public and private certificates or username and password. autoaccept is boolean
//which if is true will automatically accept if there is prompt for new certificate, otherwise will prompt
//the user to either agree or disagree. custProps is array of custom properties. Returns the ID of the new
//host that is added and error.
func AddHost(ipF, resPoolID, deplPolicyID, credID, publicCert, privateCert, userName, passWord string,
	autoAccept bool,
	custProps []string) (string, error) {

	url := config.URL + "/resources/hosts"

	if ok, err := allFlagReadyHost(ipF, resPoolID); !ok {
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
			return "", errors.New("Credentials ID not provided.")
		}
	} else {
		newCredID = credID
	}

	rp := functions.CreateResLinkForRP(resPoolID)

	dp := functions.CreateResLinkForDP(deplPolicyID)

	cred := functions.CreateResLinkForCredentials(newCredID)

	hostProps := properties.ParseCustomProperties(custProps)
	if hostProps == nil {
		hostProps = make(map[string]*string)
	}
	hostProps = properties.MakeHostProperties(cred, dp, hostProps)

	host := HostState{
		Id:               ipF,
		Address:          ipF,
		ResourcePoolLink: rp,
		CustomProperties: hostProps,
	}

	hostObj := HostObj{
		HostState: host,
	}

	jsonBody, err := json.Marshal(hostObj)
	functions.CheckJson(err)

	req, _ := http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	if resp.StatusCode == 200 {
		checkRes := certificates.CheckTrustCert(respBody, autoAccept)
		if checkRes {
			req, _ := http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
			resp, respBody = client.ProcessRequest(req)
			if resp.StatusCode != 204 {
				credentials.RemoveCredentialsID(newCredID)
				return "", errors.New("Error occured when adding host.")
			}
			link := resp.Header.Get("Location")
			url = config.URL + link
			req, _ = http.NewRequest("GET", url, nil)
			_, respBody = client.ProcessRequest(req)
			addedHost := &Host{}
			err = json.Unmarshal(respBody, addedHost)
			functions.CheckJson(err)
			return addedHost.Id, nil
		}
		credentials.RemoveCredentialsID(newCredID)
		return "", errors.New("Error occured when adding the new certificate.")
	} else if resp.StatusCode == 204 {
		link := resp.Header.Get("Location")
		url = config.URL + link
		req, _ = http.NewRequest("GET", url, nil)
		_, respBody = client.ProcessRequest(req)
		addedHost := &Host{}
		err = json.Unmarshal(respBody, addedHost)
		functions.CheckJson(err)
		return addedHost.Id, nil
	}
	return "", errors.New("Error occured when adding host.")
}

//RemoveHost removes host by address passed as parameter, the other parameter is boolean
//to specify if you want to do it as async operation or the program should wait until
//the host is added. Returns the address of the removed host and error = nil, or empty string
//and error != nil.
func RemoveHost(hostAddress string, asyncTask bool) (string, error) {
	url := config.URL + "/requests"

	link := functions.CreateResLinksForHosts(hostAddress)

	jsonRemoveHost := &containers.OperationContainer{
		Operation:     "REMOVE_RESOURCE",
		ResourceLinks: []string{link},
		ResourceType:  "CONTAINER_HOST",
	}

	jsonBody, err := json.Marshal(jsonRemoveHost)

	functions.CheckJson(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode == 200 {
		taskStatus := &track.OperationResponse{}
		_ = json.Unmarshal(respBody, taskStatus)
		taskStatus.PrintTracerId()
		if !asyncTask {
			track.Wait(taskStatus.GetTracerId())
			return hostAddress, nil
		}
	}
	return "", errors.New("Error occured when removing host.")
}

func DisableHost(hostAddress string) (string, error) {
	//hostAddress := getHostAddress(name)
	url := config.URL + "/resources/compute/" + hostAddress
	hostp := HostPatch{
		PowerState: "SUSPEND",
	}
	jsonBody, err := json.Marshal(hostp)
	functions.CheckJson(err)

	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	resp, _ := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode == 200 {
		return hostAddress, nil
	}
	return "", errors.New("Error occurred, host was not disabled.")
}

func EnableHost(hostAddress string) (string, error) {
	//hostAddress := getHostAddress(name)
	url := config.URL + "/resources/compute/" + hostAddress
	hostp := HostPatch{
		PowerState: "ON",
	}
	jsonBody, err := json.Marshal(hostp)
	functions.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	resp, _ := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode == 200 {
		return hostAddress, nil
	}
	return "", errors.New("Error occurred, host was not enabled.")
}

func GetPublicCustomProperties(address string) map[string]*string {
	props := GetCustomProperties(address)
	if props == nil {
		return nil
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
	return pubProps
}

func GetCustomProperties(address string) map[string]*string {
	link := functions.CreateResLinksForHosts(address)
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	resp, respBody := client.ProcessRequest(req)
	if resp.StatusCode != 200 {
		return nil
	}
	host := &Host{}
	err := json.Unmarshal(respBody, host)
	functions.CheckJson(err)
	return host.CustomProperties
}

func AddCustomProperties(address string, keys, vals []string) bool {
	link := functions.CreateResLinksForHosts(address)
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
	functions.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	resp, _ := client.ProcessRequest(req)
	if resp.StatusCode != 200 {
		return false
	}
	return true
}

func RemoveCustomProperties(address string, keys []string) bool {
	link := functions.CreateResLinksForHosts(address)
	url := config.URL + link
	custProps := make(map[string]*string)
	for i := range keys {
		custProps[keys[i]] = nil
	}
	host := &Host{
		CustomProperties: custProps,
	}
	jsonBody, err := json.Marshal(host)
	functions.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	resp, _ := client.ProcessRequest(req)
	if resp.StatusCode != 200 {
		return false
	}
	return true
}

func EditHost(ipF, name, resPoolF, deplPolicyF, credName string,
	autoAccept bool) (string, error) {
	url := config.URL + "/resources/compute/" + ipF
	props, err := MakeUpdateHostProperties(deplPolicyF, credName, name)
	if err != nil {
		return "", err
	}

	var (
		rpLinks []string
		rpLink  string
	)
	if resPoolF != "" {
		rpLinks = resourcePools.GetRPLinks(resPoolF)
		if len(rpLinks) < 1 {
			return "", errors.New("Resource pool not found.")
		}
		if len(rpLinks) > 1 {
			return "", errors.New("Multiple resource pools found with that name, resolve it manually and then proceed.")
		}
		rpLink = rpLinks[0]
	}

	newHost := &HostUpdate{
		ResourcePoolLink: rpLink,
		CustomProperties: props,
	}
	jsonBody, err := json.Marshal(newHost)
	functions.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	req.Header.Add("Pragma", "xn-force-index-update")
	resp, _ := client.ProcessRequest(req)

	if resp.StatusCode != 200 {
		return "", errors.New("Error occured when updating host.")
	}
	return ipF, nil
}

func allFlagReadyHost(ipF, resPoolF string) (bool, error) {
	if ipF == "" {
		return false, errors.New("IP address not provided.")
	}
	if resPoolF == "" {
		return false, errors.New("Resource pool ID not provided.")
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
		dpLinks := deplPolicy.GetDPLinks(dp)
		if len(dpLinks) < 1 {
			return nil, errors.New("Deployment policy not found.")
		} else if len(dpLinks) > 1 {
			return nil, errors.New("Multiple deployment policy found with that name, resolve it manually and then proceed.")
		}
		props["__deploymentPolicyLink"] = &dpLinks[0]
	}

	if cred != "" {
		credLinks := credentials.GetCredentialsLinks(cred)
		if len(credLinks) < 1 {
			return nil, errors.New("Credentials not found.")
		} else if len(credLinks) > 1 {
			return nil, errors.New("Multiple credentials found with that name, resolve it manually and then proceed.")
		}
		props["__authCredentialsLink"] = &credLinks[0]
	}

	if name != "" {
		props["__hostAlias"] = &name
	}

	return props, nil
}
