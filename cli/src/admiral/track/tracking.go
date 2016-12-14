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

package track

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"admiral/client"
	"admiral/config"
	"admiral/events"
	"admiral/utils"
)

type FailureMessage struct {
	Message string `json:"message"`
}

type TaskInfo struct {
	Stage   string         `json:"stage"`
	Failure FailureMessage `json:"failure"`
}

type TaskStatus struct {
	Operation     string   `json:"operation"`
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

// Operations that are going against RequestBrokerService,
// returns link where you can track the request you made.
// When you have sent operation to the RequestBrokerService,
// invoke this function by passing the response body.
// StartWaitingFromResponse will unmarshal the response body,
// which is passed as parameter get the track link, and start waiting the task.
// Finally it will return string slice containing the resource links,
// and possible error, which occurred during the waiting,
// or the request ended with with error.
func StartWaitingFromResponseBody(respBody []byte) ([]string, error) {
	opResp := getOperationResponse(respBody)
	if !utils.Quiet {
		opResp.PrintTracerId()
	}
	resLinks, err := Wait(opResp.GetTracerId())
	return utils.GetResourceIDs(resLinks), err
}

func PrintTaskIdFromResponseBody(respBody []byte) {
	if utils.Quiet {
		return
	}
	opResp := getOperationResponse(respBody)
	opResp.PrintTracerId()
}

func getOperationResponse(respBody []byte) *OperationResponse {
	opResp := &OperationResponse{}
	json.Unmarshal(respBody, opResp)
	return opResp
}

const (
	SubstageCompleted          = "COMPLETED"
	SubstageError              = "ERROR"
	ProvisionResourceOperation = "PROVISION_RESOURCE"
)

func Wait(taskId string) ([]string, error) {
	const progressBarWidth = 55
	taskStatus := &TaskStatus{}
	var (
		result        string
		resourceLinks []string
		err           error
	)
	pb := ProgressBar{progressBarWidth}
	pb.InitPrint()

	url := config.URL + "/request-status/" + taskId
	req, _ := http.NewRequest("GET", url, nil)

	begin := time.Now()
	for {
		elapsed := time.Now().Sub(begin)
		if elapsed.Seconds() > float64(config.TASK_TIMEOUT) {
			return nil, errors.New("Task timed out.")
		}

		_, respBody, respErr := client.ProcessRequest(req)
		if respErr != nil {
			return nil, respErr
		}

		err = json.Unmarshal(respBody, taskStatus)
		utils.CheckBlockingError(err)
		pb.UpdateBar(taskStatus.Progress)

		if isTaskCompleted(taskStatus) {
			result = taskStatus.SubStage
			resourceLinks = taskStatus.ResourceLinks
			pb.FillUp()
			break
		} else if taskStatus.SubStage == SubstageError {
			result = taskStatus.SubStage
			err = getErrorMessage(req)
			break
		}
		time.Sleep(1000 * time.Millisecond)
	}

	if !utils.Quiet {
		fmt.Printf("\n%s The task has %s.\n", time.Now().Format("2006.01.02 15:04:05"), result)
	}

	return resourceLinks, err
}

func getErrorMessage(statusReq *http.Request) error {
	//Wait because sometimes event log link is not generated.
	time.Sleep(1 * time.Second)
	_, respBody, respErr := client.ProcessRequest(statusReq)
	if respErr != nil {
		return respErr
	}
	taskStatus := &TaskStatus{}
	err := json.Unmarshal(respBody, taskStatus)
	utils.CheckBlockingError(err)
	if taskStatus.EventLogLink == "" {
		return errors.New("No event log link.")
	}
	url := config.URL + taskStatus.EventLogLink
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr = client.ProcessRequest(req)
	if respErr != nil {
		return respErr
	}
	event := &events.EventInfo{}
	err = json.Unmarshal(respBody, event)
	utils.CheckBlockingError(err)
	return errors.New(event.Description)
}

func isTaskCompleted(taskStatus *TaskStatus) bool {
	if taskStatus.SubStage == SubstageCompleted && taskStatus.Operation == ProvisionResourceOperation {
		if taskStatus.ResourceLinks == nil || len(taskStatus.ResourceLinks) == 0 {
			return false
		}
		return true
	} else if taskStatus.SubStage == SubstageCompleted && taskStatus.Operation != ProvisionResourceOperation {
		return true
	}
	return false
}

type ProgressBar struct {
	Widht int
}

func (pb *ProgressBar) InitPrint() {
	if utils.Quiet {
		return
	}
	fmt.Print("|>")
	for i := 0; i < pb.Widht; i++ {
		fmt.Print("-")
	}
	fmt.Print("|")
}

func (pb *ProgressBar) UpdateBar(percentage int) {
	if utils.Quiet {
		return
	}
	fmt.Print("\r|")
	result := int((float32(percentage) / 100) * float32(pb.Widht))
	if result == pb.Widht && percentage == 100 {
		pb.FillUp()
		return
	}
	for i := 0; i < result; i++ {
		fmt.Print("=")
	}
	fmt.Print(">")
	for i := result; i < pb.Widht; i++ {
		fmt.Print("-")
	}
	fmt.Print("|    ", percentage, "%")
}

func (pb *ProgressBar) FillUp() {
	if utils.Quiet {
		return
	}
	fmt.Print("\r|")
	for i := 0; i < pb.Widht; i++ {
		fmt.Print("=")
	}
	fmt.Print(">|    ", 100, "%")
}
