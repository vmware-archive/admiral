package apps

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/containers"
	"admiral/functions"
)

type App struct {
	Name                     string   `json:"name"`
	CompositeDescriptionLink string   `json"compositeDescriptionLink"`
	ComponentLinks           []string `json:"componentLinks"`
}

type ListApps struct {
	TotalCount    int32          `json:"totalCount"`
	DocumentLinks []string       `json:"documentLinks"`
	Documents     map[string]App `json:"documents"`
}

func (la *ListApps) FetchApps(queryF string) int {
	url := config.URL + "/resources/composite-components?documentType=true&$count=true&$limit=21&$orderby=documentSelfLink+asc"
	var query string
	if strings.TrimSpace(queryF) != "" {
		query = fmt.Sprintf("&$filter=ALL_FIELDS+eq+'*%s*'", queryF)
		url = url + query
	}
	req, _ := http.NewRequest("GET", url, nil)
	resp, respBody := client.ProcessRequest(req)
	err := json.Unmarshal(respBody, la)
	functions.CheckJson(err)
	defer resp.Body.Close()
	return len(la.DocumentLinks)
}

//Function to get links of applications, matching the name from argument.
func (la *ListApps) GetMatchingNames(name string) []string {
	links := make([]string, 0)
	for link, app := range la.Documents {
		if app.Name == name {
			links = append(links, link)
		}
	}
	return links
}

//Function to print active applications without the containers in the apps.
func (listApps *ListApps) PrintActiveWithoutContainer() {
	fmt.Printf("%-40s %-18s \n", "ID", "NAME")
	for link, app := range listApps.Documents {
		fmt.Printf("%-40s %-18s \n", GetIdFromApp(link), app.Name)
	}
}

//Function to print the active applications with the containers in the apps.
func (listApps *ListApps) PrintActiveWithContainer() {
	indent := "\u251c\u2500\u2500"
	url := config.URL
	fmt.Printf("%-40s %-18s \n", "ID", "NAME")
	for link, app := range listApps.Documents {
		fmt.Printf("%-40s %-18s \n", GetIdFromApp(link), app.Name)

		if len(app.ComponentLinks) < 1 {
			continue
		}
		fmt.Printf("%s%-40s %-15s %-8s %-17s %-17s %s\n",
			indent, "NAME", "ADDRESS", "STATUS", "CREATED", "STARTED", "[HOST:CONTAINER]")
		for _, cntr := range app.ComponentLinks {
			containerUrl := url + cntr
			container := &containers.Container{}
			req, _ := http.NewRequest("GET", containerUrl, nil)
			resp, respBody := client.ProcessRequest(req)
			defer resp.Body.Close()
			err := json.Unmarshal(respBody, container)
			functions.CheckJson(err)
			fmt.Printf("%s%-40s %-15s %-8s %-17s %-17s %s\n", indent,
				strings.Join(container.Names, " "), container.Address, container.PowerState, container.GetCreated(), container.GetStarted(), container.Ports)
		}

	}

}
