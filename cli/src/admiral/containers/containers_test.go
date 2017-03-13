// +build integration

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
	"os"
	"testing"

	"admiral/auth"
	. "admiral/common"
	. "admiral/common/utils"
	"admiral/config"
	"admiral/credentials"
	"admiral/hosts"
)

var tc = &TestConfig{}

func TestMain(m *testing.M) {
	var err error
	tc, err = ConfigureTestEnv()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	IsTest = true
	config.GetCfgForTests()
	auth.Login(tc.Username, tc.Password, tc.AdmiralAddress)

	code := m.Run()

	os.Exit(code)
}

func TestProvisionRemoveContainer(t *testing.T) {
	// Preparation
	credentialsID, err := credentials.AddByCert("test-credentials", tc.PublicKey, tc.PrivateKey, nil)
	CheckTestError(err, t)
	hostID, err := hosts.AddHost(tc.HostAddress, tc.PlacementZone, "docker", "", credentialsID, "", "", "", "", true, nil, nil)
	CheckTestError(err, t)
	containerName := "ubuntu"
	imageName := "ubuntu"
	cd := ContainerDescription{
		Name:  NilString{containerName},
		Image: NilString{imageName},
	}

	// Testing phase 1
	contId, err := cd.RunContainer("", false)
	CheckTestError(err, t)

	// Validating phase 1
	lc := ContainersList{}
	lc.FetchContainers("")
	exist := false
	for _, cont := range lc.Documents {
		if cont.GetID() == contId {
			exist = true
			break
		}
	}

	if !exist {
		t.Error("Provisioned container is not found.")
	}

	// Testing phase 2
	contIds, err := RemoveContainer([]string{contId}, false)
	CheckTestError(err, t)

	// Validating phase 2
	lc = ContainersList{}
	lc.FetchContainers("")
	exist = false
	for _, cont := range lc.Documents {
		if cont.GetID() == contIds[0] {
			exist = true
			break
		}
	}

	if exist {
		t.Error("Removed container is found.")
	}

	// Cleaning
	_, err = hosts.RemoveHost(hostID, false)
	CheckTestError(err, t)
	err = hosts.ValidateHostIsDeleted(hostID)
	CheckTestError(err, t)
	_, err = credentials.RemoveCredentialsID(credentialsID)
	CheckTestError(err, t)
}

func TestStopStartContainer(t *testing.T) {
	// Preparation
	credentialsID, err := credentials.AddByCert("test-credentials", tc.PublicKey, tc.PrivateKey, nil)
	CheckTestError(err, t)
	hostID, err := hosts.AddHost(tc.HostAddress, tc.PlacementZone, "docker", "", credentialsID, "", "", "", "", true, nil, nil)
	CheckTestError(err, t)
	containerName := "ubuntu"
	imageName := "ubuntu"
	cd := ContainerDescription{
		Name:  NilString{containerName},
		Image: NilString{imageName},
	}
	contId, err := cd.RunContainer("", false)
	CheckTestError(err, t)

	// Testing phase 1
	contIds, err := StopContainer([]string{contId}, false)
	CheckTestError(err, t)

	// Validating phase 1
	lc := ContainersList{}
	lc.FetchContainers("")
	exist := false
	for _, cont := range lc.Documents {
		if cont.GetID() == contIds[0] {
			if cont.PowerState != "STOPPED" {
				t.Errorf("Expected container state: STOPPED, actual container state: %s", cont.PowerState)
			}
			exist = true
			break
		}
	}

	if !exist {
		t.Error("Provisioned container is not found.")
	}

	// Testing phase 2
	contIds, err = StartContainer([]string{contId}, false)
	CheckTestError(err, t)

	// Validating phase 2
	lc = ContainersList{}
	lc.FetchContainers("")
	for _, cont := range lc.Documents {
		if cont.GetID() == contIds[0] {
			if cont.PowerState != "RUNNING" {
				t.Errorf("Expected container state: RUNNING, actual container state: %s", cont.PowerState)
			}
			break
		}
	}

	// Cleaning
	_, err = RemoveContainer([]string{contId}, false)
	CheckTestError(err, t)
	_, err = hosts.RemoveHost(hostID, false)
	CheckTestError(err, t)
	err = hosts.ValidateHostIsDeleted(hostID)
	CheckTestError(err, t)
	_, err = credentials.RemoveCredentialsID(credentialsID)
	CheckTestError(err, t)
}
