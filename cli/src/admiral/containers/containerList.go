package containers

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/functions"
)

type ListContainers struct {
	TotalCount    int64                `json:"totalCount"`
	Documents     map[string]Container `json:"documents"`
	DocumentLinks []string             `json:"documentLinks"`
}

//FetchContainers fetches containers by given query which is passed as parameter.
//In case you want to fetch all containers, pass empty string as parameter.
//The return result is the count of fetched containers.
func (lc *ListContainers) FetchContainers(queryF string) int {
	url := config.URL + "/resources/containers?documentType=true&$count=true&$limit=10000&$orderby=documentSelfLink+asc"

	var query string
	if strings.TrimSpace(queryF) != "" {
		query = fmt.Sprintf("&$filter=ALL_FIELDS+eq+'*%s*'", queryF)
		url = url + query
	}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody := client.ProcessRequest(req)
	err := json.Unmarshal(respBody, lc)
	functions.CheckJson(err)

	count := len(lc.DocumentLinks)

	return count - 1
}

//Print is printing the containers to the console. It takes boolean
//parameter. If it's true will print all the containers. If it's false
//will print only the running containers.
func (lc *ListContainers) Print(allContainers bool) {
	if allContainers {
		fmt.Printf("%-40s %-40s %-15s %-15s %-17s %-17s %-20s %s\n",
			"ID", "NAME", "ADDRESS", "STATUS", "CREATED", "STARTED", "[HOST:CONTAINER]", "EXTERNAL ID")

		for _, link := range lc.DocumentLinks {
			val := lc.Documents[link]
			if val.System {
				continue
			}
			fmt.Printf("%-40s %-40s %-15s %-15s %-17s %-17s %-20s %s\n", val.GetID(), strings.Join(val.Names, " "), val.Address, val.PowerState,
				val.GetCreated(), val.GetStarted(), val.GetPorts(), val.Id)
		}
	} else {
		fmt.Printf("%-40s %-40s %-15s %-15s %-17s %-17s %-20s %s\n",
			"ID", "NAME", "ADDRESS", "STATUS", "CREATED", "STARTED", "[HOST:CONTAINER]", "EXTERNAL ID")
		for _, link := range lc.DocumentLinks {
			val := lc.Documents[link]
			if val.System {
				continue
			}
			if val.PowerState == "RUNNING" {
				fmt.Printf("%-40s %-40s %-15s %-15s %-17s %-17s %-20s %s\n",
					val.GetID(), strings.Join(val.Names, " "), val.Address, val.PowerState,
					val.GetCreated(), val.GetStarted(), val.GetPorts(), val.Id)
			}
		}
	}
}

//Function to get container description if the name is equal to one passed in parameter.
func (lc *ListContainers) GetContainerDescription(name string) string {
	for _, cont := range lc.Documents {
		if name == cont.Names[0] {
			return cont.DescriptionLink
		}
	}
	return ""
}

//Function to get container self link if name is equal to one passed in parameter.
func (lc *ListContainers) GetContainerLink(name string) string {
	for _, link := range lc.DocumentLinks {
		val := lc.Documents[link]
		if name == val.Names[0] {
			return link
		}
	}
	return ""
}

//Function to verify if the given container exists.
//Return result is boolean which is true if it exists and false if it doesn't exist.
func (lc *ListContainers) VerifyExistingContainer(names []string) bool {
	for _, containerName := range names {
		for _, val := range lc.Documents {
			for _, currName := range val.Names {
				if currName == containerName {
					return true
				}
			}
		}
	}
	return false
}
