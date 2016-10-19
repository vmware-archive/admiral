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

package containers

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/deplPolicy"
	"admiral/images"
	"admiral/track"
	"admiral/utils"
)

type LogConfig struct {
	Type utils.NilString `json:"type"`
}

func (lc *LogConfig) SetType(s string) {
	if s == "" || s == "none" || s == "json-file" ||
		s == "syslog" || s == "journald" || s == "gelf" ||
		s == "fluentd" || s == "awslogs" || s == "splunk" ||
		s == "etwlogs" || s == "gcplogs" {
		lc.Type = utils.NilString{s}
	} else {
		fmt.Println("Invalid log driver.")
		os.Exit(0)
	}
}

//Note: nil types are from "admiral/nulls" package.
//Note: "dot import" is used for cleaner code.
type ContainerDescription struct {
	Image              utils.NilString `json:"image"`
	Name               utils.NilString `json:"name"`
	Cluster            utils.NilInt32  `json:"_cluster"`
	Command            []string        `json:"command"`
	CpuShares          utils.NilString `json:"cpuShares"`
	DeploymentPolicyID utils.NilString `json:"deploymentPolicyId"`
	Env                []string        `json:"env"`
	ExposeService      []string        `json:"exposeService"`
	Hostname           utils.NilString `json:"hostname"`
	Links              []string        `json:"links"`
	LogConfig          LogConfig       `json:"logConfig"`
	MaximumRetryCount  utils.NilInt32  `json:"maximumRetryCount"`
	MemoryLimit        utils.NilInt64  `json:"memoryLimit"`
	MemorySwapLimit    utils.NilInt64  `json:"memorySwapLimit"`
	NetworkMode        utils.NilString `json:"networkMode"`
	PortBindings       []Port          `json:"portBindings"`
	PublishAll         bool            `json:"publishAll"`
	RestartPolicy      utils.NilString `json:"restartPolicy"`
	WorkingDir         utils.NilString `json:"workingDir"`
	Volumes            []string        `json:"volumes"`
}

func (cd *ContainerDescription) Create(
	imgName, name, cpuShares, networkMode, restartPol, workingDir, logDriver, hostName, deplPolicyF string,
	clusterSize, retryCount int32,
	memory, memorySwap int64,
	cmds, env, volumes, ports []string,
	publishAll bool) {

	//Begin setting up array of port objects.
	portArr := make([]Port, 0)
	if len(ports) > 0 {
		for _, p := range ports {
			currPort := Port{}
			currPort.SetPorts(p)
			portArr = append(portArr, currPort)
		}
	}
	//End setting up array of port objects.
	logconf := LogConfig{}
	logconf.SetType(logDriver)

	//Restart policy validation.
	if restartPol != "no" && restartPol != "always" && restartPol != "on-failure" {
		fmt.Println("Invalid restart policy.")
		os.Exit(0)
	}

	//Network mode validation.
	if networkMode != "none" && networkMode != "host" && networkMode != "bridge" {
		fmt.Println("Invalid network mode.")
		os.Exit(0)
	}

	//Deployment policy validation.
	var dp string
	if deplPolicyF != "" {
		dpLinks := deplPolicy.GetDPLinks(deplPolicyF)
		if len(dpLinks) > 1 {
			fmt.Println("Deployment policy have duplicate names, please resolve this issue.")
			os.Exit(0)
		} else if len(dpLinks) < 1 {
			fmt.Println("Deployment policy not found.")
			os.Exit(0)
		}
		dp = strings.Replace(dpLinks[0], "/resources/deployment-policies/", "", -1)
	}

	cd.Image = utils.NilString{imgName}
	cd.Name = utils.NilString{name}
	cd.Cluster = utils.NilInt32{clusterSize}
	cd.Command = cmds
	cd.CpuShares = utils.NilString{cpuShares}
	cd.DeploymentPolicyID = utils.NilString{dp}
	cd.Env = env
	cd.Hostname = utils.NilString{hostName}
	cd.LogConfig = logconf
	cd.MaximumRetryCount = utils.NilInt32{retryCount}
	cd.MemoryLimit = utils.NilInt64{memory}
	cd.MemorySwapLimit = utils.NilInt64{memorySwap}
	cd.NetworkMode = utils.NilString{networkMode}
	cd.PublishAll = publishAll
	cd.PortBindings = portArr
	cd.RestartPolicy = utils.NilString{restartPol}
	cd.WorkingDir = utils.NilString{workingDir}
	cd.Volumes = volumes
}

func (cd *ContainerDescription) RunContainer(projectId string, asyncTask bool) (string, error) {
	linkToRun, err := getContaierRunLink(cd)
	if err != nil {
		return "", err
	}
	var tenantLinks []string
	if projectId != "" {
		tenantLinks = make([]string, 0)
		projectLink := utils.CreateResLinkForProject(projectId)
		tenantLinks = append(tenantLinks, projectLink)
	}
	url := config.URL + "/requests"
	runContainer := &RunContainer{
		ResourceType:            "DOCKER_CONTAINER",
		ResourceDescriptionLink: linkToRun,
		TenantLinks:             tenantLinks,
	}

	jsonBody, err := json.MarshalIndent(runContainer, "", "    ")
	utils.CheckJson(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	taskStatus := &track.OperationResponse{}
	_ = json.Unmarshal(respBody, taskStatus)
	taskStatus.PrintTracerId()
	if !asyncTask {
		resLinks, err = track.Wait(taskStatus.GetTracerId())
	} else {
		resLinks, err = track.GetResLinks(taskStatus.GetTracerId())
	}
	if len(resLinks) > 0 {
		return utils.GetResourceID(resLinks[0]), err
	}
	return "", err

}

func getContaierRunLink(cd *ContainerDescription) (string, error) {
	var runLink string
	url := config.URL + "/resources/container-descriptions"
	jsonBody, err := json.MarshalIndent(cd, "", "    ")
	utils.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	req.Header.Set("Pragma", "xn-force-index-update")
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	image := &images.Image{}
	_ = json.Unmarshal(respBody, image)
	runLink = image.DocumentSelfLink
	return runLink, nil

}
