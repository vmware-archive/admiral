package apps

import (
	"bytes"
	"encoding/json"
	"net/http"
	"strings"

	"errors"
	"admiral/client"
	"admiral/config"
	"admiral/containers"
	"admiral/functions"
	"admiral/templates"
	"admiral/track"
)

var (
	duplMsg     = "Duplicates found, provide the ID of the specific aplication."
	notFoundMsg = "Application not found."
)

//Function to start application.
//For parameter takes application name and bool to trigger or not waiting for task.
//Returns bool to specify if app is starting.
func StartApp(name string, asyncTask bool) ([]string, error) {
	resourceLinks := GetAppLinks(name)
	if len(resourceLinks) > 1 {
		return nil, errors.New(duplMsg)
	} else if len(resourceLinks) < 1 {
		return nil, errors.New(notFoundMsg)
	}

	id := functions.GetResourceID(resourceLinks[0])
	return StartAppID(id, asyncTask)
}

//Same as StartApp() but takes app's ID in order to avoid conflict from duplicate names.
func StartAppID(id string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	resourceLinks := functions.CreateResLinksForApps([]string{id})
	var (
		resLinks []string
		err      error
	)
	oc := &containers.OperationContainer{
		Operation:     "Container.Start",
		ResourceLinks: resourceLinks,
		ResourceType:  "COMPOSITE_COMPONENT",
	}

	jsonBody, err := json.Marshal(oc)
	functions.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode == 200 {
		taskStatus := &track.OperationResponse{}
		_ = json.Unmarshal(respBody, taskStatus)
		taskStatus.PrintTracerId()
		if !asyncTask {
			resLinks, err = track.Wait(taskStatus.GetTracerId())
			resLinks = []string{id}
		} else {
			resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
			resLinks = []string{id}
		}
	} else {
		resLinks = nil
		err = errors.New("Error occured when starting application.")
	}
	return resLinks, err
}

//Function to stop application.
//For parameter takes application name and bool to trigger or not waiting for task.
//Returns bool to specify if app is stopping.
func StopApp(name string, asyncTask bool) ([]string, error) {
	resourceLinks := GetAppLinks(name)
	if len(resourceLinks) > 1 {
		return nil, errors.New(duplMsg)
	} else if len(resourceLinks) < 1 {
		return nil, errors.New(notFoundMsg)
	}
	id := functions.GetResourceID(resourceLinks[0])
	return StopAppID(id, asyncTask)
}

//Same as StopApp() but takes app's ID in order to avoid conflict from duplicate names.
func StopAppID(id string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	resourceLinks := functions.CreateResLinksForApps([]string{id})
	var (
		resLinks []string
		err      error
	)
	oc := &containers.OperationContainer{
		Operation:     "Container.Stop",
		ResourceLinks: resourceLinks,
		ResourceType:  "COMPOSITE_COMPONENT",
	}

	jsonBody, err := json.Marshal(oc)
	functions.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode == 200 {
		taskStatus := &track.OperationResponse{}
		_ = json.Unmarshal(respBody, taskStatus)
		taskStatus.PrintTracerId()
		if !asyncTask {
			resLinks, err = track.Wait(taskStatus.GetTracerId())
			resLinks = []string{id}
		} else {
			resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
			resLinks = []string{id}
		}

	} else {
		resLinks = nil
		err = errors.New("Error occured when stopping application.")
	}
	return resLinks, err
}

//Function to remove application.
//For parameter takes application name and bool to trigger or not waiting for task.
//Returns bool to specify if app is removing.
func RemoveApp(name string, asyncTask bool) ([]string, error) {
	resourceLinks := GetAppLinks(name)

	if len(resourceLinks) > 1 {
		return nil, errors.New(duplMsg)
	} else if len(resourceLinks) < 1 {
		return nil, errors.New(notFoundMsg)
	}

	id := functions.GetResourceID(resourceLinks[0])
	return RemoveAppID(id, asyncTask)
}

//Same as RemoveApp() but takes app's ID in order to avoid conflict from duplicate names.
func RemoveAppID(id string, asyncTask bool) ([]string, error) {
	url := config.URL + "/requests"
	resourceLinks := functions.CreateResLinksForApps([]string{id})
	var (
		resLinks []string
		err      error
	)
	oc := &containers.OperationContainer{
		Operation:     "Container.Delete",
		ResourceLinks: resourceLinks,
		ResourceType:  "COMPOSITE_COMPONENT",
	}
	jsonBody, err := json.Marshal(oc)
	functions.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode == 200 {
		taskStatus := &track.OperationResponse{}
		_ = json.Unmarshal(respBody, taskStatus)
		taskStatus.PrintTracerId()
		if !asyncTask {
			resLinks, err = track.Wait(taskStatus.GetTracerId())
			resLinks = []string{id}
		} else {
			resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
			resLinks = []string{id}
		}

	} else {
		resLinks = nil
		err = errors.New("Error occured when removing application.")
	}
	return resLinks, err
}

//Function to provision application.
//For parameter takes application name and bool to trigger or not waiting for task.
//Returns bool to specify if app is provisioning.
func RunApp(app string, asyncTask bool) ([]string, error) {
	links := queryTemplateName(app)
	if len(links) > 1 {
		return nil, errors.New("Templates with duplicate name found, provide ID to provision specific template.")
	} else if len(links) < 1 {
		return nil, errors.New("Template not found.")
	}

	id := functions.GetResourceID(links[0])
	return RunAppID(id, asyncTask)
}

//Same as RunApp() but takes app's ID in order to avoid conflict from duplicate names.
func RunAppID(id string, asyncTask bool) ([]string, error) {
	jsonBody := make(map[string]string, 0)
	link := "/resources/composite-descriptions/" + id
	jsonBody["documentSelfLink"] = link
	reqBody, err := json.Marshal(jsonBody)
	functions.CheckJson(err)

	url := config.URL + "/resources/composite-descriptions-clone"
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(reqBody))
	_, respBody := client.ProcessRequest(req)
	f := make(map[string]interface{}, 0)
	err = json.Unmarshal(respBody, f)
	link = jsonBody["documentSelfLink"]

	ra := RunApplication{
		ResourceDescriptionLink: link,
		ResourceType:            "COMPOSITE_COMPONENT",
	}
	resLinks, err := ra.run(asyncTask)
	ids := functions.GetResourceIDs(resLinks)
	return ids, err
}

func RunAppFile(dirF string, keepTemplate, asyncTask bool) ([]string, error) {
	id, _ := Import(dirF)
	resLinks, err := RunAppID(id, false)
	if !keepTemplate {
		templates.RemoveTemplateID(id)
	}
	ids := functions.GetResourceIDs(resLinks)
	return ids, err
}

//Function to get links of templates with name equal to passed in paramater.
func queryTemplateName(tmplName string) []string {
	tmplNameArr := strings.Split(tmplName, "/")
	name := tmplNameArr[len(tmplNameArr)-1]
	lt := templates.TemplatesList{}
	lt.FetchTemplates(name)
	links := lt.GetTemplateLinks(tmplName)
	return links

}

//Function to get the unique ID from the link.
//Returns the ID.
func GetIdFromApp(fullLink string) string {
	return functions.GetResourceID(fullLink)
}

func InspectID(id string) bool {
	links := functions.CreateResLinksForApps([]string{id})
	url := config.URL + links[0]
	req, _ := http.NewRequest("GET", url, nil)
	resp, respBody := client.ProcessRequest(req)
	if resp.StatusCode != 200 {
		return false
	}
	app := &App{}
	err := json.Unmarshal(respBody, app)
	functions.CheckJson(err)
	customMap := make(map[string]App)
	customMap[id] = *app
	la := ListApps{
		Documents: customMap,
	}
	la.PrintActiveWithContainer()
	return true
}

type RunApplication struct {
	ResourceDescriptionLink string `json:"resourceDescriptionLink"`
	ResourceType            string `json:"resourceType"`
}

//Function that send request to the Admiral API to provision application.
func (ra *RunApplication) run(asyncTask bool) ([]string, error) {
	var (
		links []string
		err   error
	)
	url := config.URL + "/requests"
	jsonBody, err := json.Marshal(ra)
	functions.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()

	if resp.StatusCode == 200 {
		taskStatus := &track.OperationResponse{}
		_ = json.Unmarshal(respBody, taskStatus)
		taskStatus.PrintTracerId()
		if !asyncTask {
			links, err = track.Wait(taskStatus.GetTracerId())
		} else {
			links, err = track.GetResLinks(taskStatus.GetTracerId())
		}
	} else {
		links = nil
		err = errors.New("Error occured when provisioning application.")
	}
	return links, err
}

func GetAppLinks(name string) []string {
	la := ListApps{}
	la.FetchApps("")
	links := make([]string, 0)
	for i := range la.DocumentLinks {
		val := la.Documents[la.DocumentLinks[i]]
		if val.Name == name {
			links = append(links, la.DocumentLinks[i])
		}
	}
	return links
}
