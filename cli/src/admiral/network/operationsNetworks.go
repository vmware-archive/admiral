package network

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"os"

	"admiral/auth"
	"admiral/client"
	"admiral/config"
	"admiral/functions"
)

func (nl *NetworkList) FetchNetworks() int {
	url := config.URL + "/resources/container-networks?expand"
	req, err := http.NewRequest("GET", url, nil)
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	err = json.Unmarshal(respBody, nl)
	functions.CheckJson(err)
	return len(nl.DocumentLinks)
}

func (nl *NetworkList) Print() {
	fmt.Printf("%-20s\n", "NAME")
	for _, link := range nl.DocumentLinks {
		val := nl.Documents[link]
		fmt.Printf("%-20s\n", val.Name)
	}
}

func RemoveNetwork(name string) bool {
	nl := &NetworkList{}
	nl.FetchNetworks()
	for _, link := range nl.DocumentLinks {
		val := nl.Documents[link]
		if name == val.Name {
			url := config.URL + link
			req, _ := http.NewRequest("DELETE", url, nil)
			resp, _ := client.ProcessRequest(req)
			defer resp.Body.Close()
			if resp.StatusCode != 200 {
				return false
			}
			return true
		}
	}
	return false
}

func InspectNetwork(name string) (bool, string) {
	nl := &NetworkList{}
	nl.FetchNetworks()
	for _, link := range nl.DocumentLinks {
		val := nl.Documents[link]
		if name == val.Name {
			url := config.URL + link
			req, _ := http.NewRequest("GET", url, nil)
			resp, respBody := client.ProcessRequest(req)
			if resp.StatusCode != 200 {
				return false, ""
			}
			n := &Network{}
			err := json.Unmarshal(respBody, n)
			functions.CheckJson(err)
			return true, n.String()
		}
	}
	return false, ""
}

func (n *Network) Create() (bool, string) {
	if n.Name == "" {
		fmt.Println("Network create failed. Name was not set.")
		os.Exit(0)
	}
	url := config.URL + "/resources/container-networks"
	jsonBody, err := json.Marshal(n)
	functions.CheckJson(err)

	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	msg := &auth.Error{}
	json.Unmarshal(respBody, msg)
	if resp.StatusCode != 200 {
		return false, msg.Message
	}
	return true, ""
}
