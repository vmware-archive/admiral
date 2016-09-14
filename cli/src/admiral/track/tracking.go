package track

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"strings"
	"time"

	"errors"
	"admiral/auth"
	"admiral/client"
	"admiral/config"
	"admiral/events"
	"admiral/functions"
)

type FailureMessage struct {
	Message string `json:"message"`
}

type TaskInfo struct {
	Stage   string         `json:"stage"`
	Failure FailureMessage `json:"failure"`
}

type TaskStatus struct {
	TaskInfo      TaskInfo `json:"taskInfo"`
	Progress      int      `json:"progress"`
	Name          string   `json:"name"`
	SubStage      string   `json:"subStage"`
	EventLogLink  string   `json:"eventLogLink"`
	ResourceLinks []string `json:"resourceLinks"`
}

type OperationResponse struct {
	RequestTrackerLink string `json:"requestTrackerLink"`
}

func (or *OperationResponse) GetTracerId() string {
	id := strings.Replace(or.RequestTrackerLink, "/request-status/", "", 1)
	return id
}

func (or *OperationResponse) PrintTracerId() {
	fmt.Printf("%s Task ID: %s\n", time.Now().Format("2006.01.02 15:04:05"), or.GetTracerId())
}

func Wait(taskId string) ([]string, error) {
	url := config.URL + "/request-status/" + taskId
	stop := make(chan bool)
	token, from := auth.GetAuthToken()
	req, _ := http.NewRequest("GET", url, nil)
	functions.CheckVerboseRequest(req)
	req.Header.Set("content-type", "application/json")
	req.Header.Set("x-xenon-auth-token", token)
	var (
		result        string
		errorMsg      string
		resourceLinks []string
	)
	go func() {
		fmt.Print("Waiting for task..")
		for {
			select {
			case <-stop:
				fmt.Println()
				return
			default:
				fmt.Print(".")
			}
			time.Sleep(1 * time.Second)
		}
	}()

	begin := time.Now()
	for {
		elapsed := time.Now().Sub(begin)
		if elapsed.Seconds() > float64(config.TIMEOUT) {
			//fmt.Println("Task timed out.")
			//os.Exit(0)
			return nil, errors.New("Task timed out.")
		}
		taskStatus := &TaskStatus{}
		resp, err := client.NetClient.Do(req)
		functions.CheckResponse(err)
		functions.CheckVerboseResponse(resp)
		respBody, _ := ioutil.ReadAll(resp.Body)
		//Check for authentication error.
		isAuth := auth.IsAuthorized(respBody, from)
		if !isAuth {
			os.Exit(-1)
		}
		if resp.StatusCode != 200 {
			//fmt.Println("Resource not found")
			//os.Exit(-1)
			return nil, errors.New("Resource not found.")
		}
		err = json.Unmarshal(respBody, taskStatus)
		functions.CheckJson(err)
		if taskStatus.SubStage == "COMPLETED" {
			result = taskStatus.SubStage
			resourceLinks = taskStatus.ResourceLinks
			stop <- true
			break
		} else if taskStatus.SubStage == "ERROR" {
			result = taskStatus.SubStage
			errorMsg = getErrorMessage(req)
			stop <- true
			break
		}
		time.Sleep(2 * time.Second)
	}

	fmt.Printf("%s The task has %s.\n", time.Now().Format("2006.01.02 15:04:05"), result)

	if result == "ERROR" {
		if errorMsg != "" {
			return resourceLinks, errors.New(errorMsg)
		}
	}
	return resourceLinks, nil
}

func GetResLinks(taskId string) ([]string, error) {
	url := config.URL + "/request-status/" + taskId
	token, from := auth.GetAuthToken()
	req, _ := http.NewRequest("GET", url, nil)
	functions.CheckVerboseRequest(req)
	req.Header.Set("content-type", "application/json")
	req.Header.Set("x-xenon-auth-token", token)
	var (
		result        string
		errorMsg      string
		resourceLinks []string
	)
	taskStatus := &TaskStatus{}
	resp, err := client.NetClient.Do(req)
	functions.CheckResponse(err)
	functions.CheckVerboseResponse(resp)
	respBody, _ := ioutil.ReadAll(resp.Body)
	//Check for authentication error.
	isAuth := auth.IsAuthorized(respBody, from)
	if !isAuth {
		os.Exit(-1)
	}
	if resp.StatusCode != 200 {
		return nil, errors.New("Resource not found.")
	}
	err = json.Unmarshal(respBody, taskStatus)
	functions.CheckJson(err)
	if taskStatus.SubStage == "COMPLETED" {
		result = taskStatus.SubStage
	} else if taskStatus.SubStage == "ERROR" {
		result = taskStatus.SubStage
		errorMsg = getErrorMessage(req)

	}
	resourceLinks = taskStatus.ResourceLinks

	if result == "ERROR" {
		if errorMsg != "" {
			return resourceLinks, errors.New(errorMsg)
		}
	}
	return resourceLinks, nil
}

func getErrorMessage(statusReq *http.Request) string {
	//Wait because sometimes event log link is not generated.
	time.Sleep(1 * time.Second)
	_, respBody := client.ProcessRequest(statusReq)
	taskStatus := &TaskStatus{}
	err := json.Unmarshal(respBody, taskStatus)
	functions.CheckJson(err)
	if taskStatus.EventLogLink == "" {
		return ""
	}
	url := config.URL + taskStatus.EventLogLink
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody = client.ProcessRequest(req)
	event := &events.EventInfo{}
	err = json.Unmarshal(respBody, event)
	functions.CheckJson(err)
	return event.Description
}
