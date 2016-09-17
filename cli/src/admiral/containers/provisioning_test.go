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
	"fmt"
	"strings"
	"testing"

	"admiral/config"
)

var (
	clusterSize int32
	cmds        []string
	cpuShares   string
	env         []string
	hostName    string
	retryCount  int32
	logDriver   string
	memory      int64
	memorySwap  int64
	networkMode string
	ports       []string
	publishAll  bool
	restartPol  string
	workingDir  string
	volumes     []string
)

//Function creating sample Container Description that will be provisioned.
func sampleContainerDescription() *ContainerDescription {
	imgName := "registry.hub.docker.com/library/ubuntu"
	imgNameArr := strings.Split(imgName, "/")
	name := imgNameArr[len(imgNameArr)-1]

	cd := &ContainerDescription{}
	cd.Create(
		imgName, name, cpuShares, "bridge", "always", workingDir, logDriver, hostName, //strings
		clusterSize, retryCount, //int32
		memory, memorySwap, //int64
		cmds, env, volumes, ports, //[]string
		publishAll) //bool
	return cd
}

//Function to remove any containers, before starting new test.
func preparation() {
	fmt.Println("Cleaning bellevue for incoming test...")
	config.GetCfg()
	RemoveMany("*", true)
	fmt.Println("Cleaning done.")
}

//Test if provisioning with default options is working.
//Will provision 2 default ubuntu containers.
//Then when fetch containers, count should be 2.
func TestRunCommand(t *testing.T) {
	preparation()
	lc := ListContainers{}
	cd := sampleContainerDescription()

	for i := 0; i < 3; i++ {
		cd.RunContainer(true)
	}

	lc.FetchContainers("")
	actual := lc.FetchContainers("")
	expected := 3

	if actual != expected {
		t.Errorf("Expected containers %d, actual containers %d", expected, actual)
	}
}
