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

package hosts

import (
	"fmt"
	"os"
	"testing"
	"time"

	"admiral/auth"
	. "admiral/common/utils"
	"admiral/config"
	"admiral/credentials"
	"admiral/placement_zones"
	"admiral/placements"
	"admiral/tags"
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
	placement_zones.BuildDefaultPlacementZone()
	placements.BuildDefaultPlacement()

	code := m.Run()
	os.Exit(code)
}

func TestAddRemoveHost(t *testing.T) {
	t.Skip("VBV-1952")
	// Preparing
	credentialsID, err := credentials.AddByCert("test-credentials", tc.PublicKey, tc.PrivateKey, nil)
	CheckTestError(err, t)

	// Testing phase 1
	hostID, err := AddHost(tc.HostAddress, tc.PlacementZone, "docker", "", credentialsID, "", "", "", "", true, nil, nil)
	CheckTestError(err, t)

	// Validating phase 1
	hl := HostsList{}
	hl.FetchHosts("")
	exist := false
	for _, host := range hl.Documents {
		if host.GetID() == hostID {
			exist = true
			break
		}
	}

	if !exist {
		t.Error("Added host is not found.")
	}

	// Testing phase 2
	hostID, err = RemoveHost(hostID, false)
	CheckTestError(err, t)
	err = ValidateHostIsDeleted(hostID)
	CheckTestError(err, t)

	// Validating phase 2
	hl = HostsList{}
	hl.FetchHosts("")
	exist = false
	for _, host := range hl.Documents {
		if host.GetID() == hostID {
			exist = true
			break
		}
	}

	if exist {
		t.Error("Removed host is found.")
	}

	// Cleaning
	_, err = credentials.RemoveCredentialsID(credentialsID)
	CheckTestError(err, t)
}

func TestEnableDisableHost(t *testing.T) {
	t.Skip("VBV-1952")
	// Preparing
	credentialsID, err := credentials.AddByCert("test-credentials", tc.PublicKey, tc.PrivateKey, nil)
	CheckTestError(err, t)
	hostID, err := AddHost(tc.HostAddress, tc.PlacementZone, "docker", "", credentialsID, "", "", "", "", true, nil, nil)
	CheckTestError(err, t)
	// Wait because host is being in UNKNOWN power state
	// by the time of disable attempt.
	time.Sleep(500 * time.Millisecond)

	// Testing phase 1
	hostID, err = DisableHost(hostID)
	CheckTestError(err, t)

	// Validating phase 1
	hl := HostsList{}
	hl.FetchHosts("")
	for _, host := range hl.Documents {
		if host.GetID() == hostID {
			if host.PowerState != "SUSPEND" {
				t.Errorf("Expected host state: SUSPEND, actual state: %s", host.PowerState)
			}
			break
		}
	}

	// Testing phase 2
	hostID, err = EnableHost(hostID)
	CheckTestError(err, t)

	// Validating phase 2
	time.Sleep(500 * time.Millisecond)
	hl = HostsList{}
	hl.FetchHosts("")
	for _, host := range hl.Documents {
		if host.GetID() == hostID {
			if host.PowerState != "ON" {
				t.Errorf("Expected host state: ON, actual state: %s", host.PowerState)
			}
			break
		}
	}

	// Cleaning
	_, err = RemoveHost(hostID, false)
	CheckTestError(err, t)
	err = ValidateHostIsDeleted(hostID)
	CheckTestError(err, t)
	_, err = credentials.RemoveCredentialsID(credentialsID)
	CheckTestError(err, t)
}

func TestHostUpdate(t *testing.T) {
	// Preparing
	credentialsID, err := credentials.AddByCert("test-credentials", tc.PublicKey, tc.PrivateKey, nil)
	CheckTestError(err, t)
	hostID, err := AddHost(tc.HostAddress, tc.PlacementZone, "docker", "", credentialsID, "", "", "", "", true, nil, nil)
	CheckTestError(err, t)
	rpName := "test-placement-zone"
	pzID, err := placement_zones.AddPZ(rpName, false, nil, nil, nil)
	CheckTestError(err, t)
	credentialsName := "test-credentials"
	credentialsUsername := "testuser"
	credentialsPassword := "testpassword"
	newCredentialsID, err := credentials.AddByUsername(credentialsName, credentialsUsername, credentialsPassword, nil)
	CheckTestError(err, t)

	// Testing
	hostID, err = EditHost(hostID, "", pzID, "", newCredentialsID, true, nil, nil)
	CheckTestError(err, t)

	// Validating
	hl := &HostsList{}
	hl.FetchHosts("")
	exist := false
	for _, host := range hl.Documents {
		if host.GetID() == hostID {
			exist = true
			if host.GetResourcePoolID() != pzID {
				t.Errorf("Expected updated placement zone ID: %s, actual placement zone ID: %s",
					pzID, host.GetResourcePoolID())
			}
			if host.GetCredentialsID() != newCredentialsID {
				t.Errorf("Expected updated credentials ID: %s, actual credentials ID: %s",
					newCredentialsID, host.GetCredentialsID())
			}
			break
		}
	}
	if !exist {
		t.Error("Updated host is not found.")
	}

	// Cleaning
	_, err = RemoveHost(hostID, false)
	CheckTestError(err, t)
	err = ValidateHostIsDeleted(hostID)
	CheckTestError(err, t)
	_, err = placement_zones.RemovePZID(pzID)
	CheckTestError(err, t)
	_, err = credentials.RemoveCredentialsID(credentialsID)
	CheckTestError(err, t)
	_, err = credentials.RemoveCredentialsID(newCredentialsID)
	CheckTestError(err, t)
}

func TestAddAndUpdateHostWithTags(t *testing.T) {
	// Preparing
	credentialsID, err := credentials.AddByCert("test-credentials", tc.PublicKey, tc.PrivateKey, nil)
	CheckTestError(err, t)

	// Testing phase 1
	hostTags := []string{"test:test", "test1:test1"}
	hostID, err := AddHost(tc.HostAddress, tc.PlacementZone, "docker", "", credentialsID, "", "", "", "", true, nil, hostTags)
	CheckTestError(err, t)

	// Validating phase 1
	hl := HostsList{}
	hl.FetchHosts("")
	addedHost := Host{}
	exist := false
	for _, host := range hl.Documents {
		if host.GetID() == hostID {
			exist = true
			addedHost = host
			break
		}
	}

	if !exist {
		t.Error("Added host is not found.")
	}

	expectedTagsOutput := "[test:test][test1:test1]"
	actualTagsOutput := tags.TagsToString(addedHost.TagLinks)

	if expectedTagsOutput != actualTagsOutput {
		t.Errorf("Expected host tags: %s, actual host tags: %s", expectedTagsOutput, actualTagsOutput)
	}

	// Testing phase 2
	tagsToAdd := []string{"newTag:newTag"}
	tagsToRemove := []string{"test:test", "test1:test1"}
	hostID, err = EditHost(hostID, "", tc.PlacementZone, "", credentialsID, true, tagsToAdd, tagsToRemove)
	CheckTestError(err, t)

	// Validating phase 2
	hl = HostsList{}
	hl.FetchHosts("")
	addedHost = Host{}
	exist = false
	for _, host := range hl.Documents {
		if host.GetID() == hostID {
			exist = true
			addedHost = host
			break
		}
	}

	if !exist {
		t.Error("Updated host is not found.")
	}

	expectedTagsOutput = "[newTag:newTag]"
	actualTagsOutput = tags.TagsToString(addedHost.TagLinks)

	if expectedTagsOutput != actualTagsOutput {
		t.Errorf("Expected updated host tags: %s, actual updated host tags: %s", expectedTagsOutput, actualTagsOutput)
	}

	_, err = RemoveHost(hostID, false)
	CheckTestError(err, t)
	err = ValidateHostIsDeleted(hostID)
	CheckTestError(err, t)
	_, err = credentials.RemoveCredentialsID(credentialsID)
	CheckTestError(err, t)
}

func TestGetName(t *testing.T) {
	custProps := make(map[string]*string)

	host := &Host{
		CustomProperties: custProps,
		Address:          "https://127.0.0.1:2376",
	}

	var newName, name string

	newName = "127.0.0.1:2376"

	name = host.GetName()
	if name != newName {
		t.Errorf("Expected host name: %s, actual name: %s", newName, name)
	}
	newName = "CustomPropName"
	custProps["__Name"] = &newName

	name = host.GetName()
	if name != newName {
		t.Errorf("Expected host name: %s, actual name: %s", newName, name)
	}

	newName = "CustomPropNameAlias"
	custProps["__hostAlias"] = &newName

	name = host.GetName()
	if name != "CustomPropNameAlias" {
		t.Errorf("Expected host name: %s, actual name: %s", newName, name)
	}

	newName = "Name"
	host.Name = newName

	name = host.GetName()
	if name != "Name" {
		t.Errorf("Expected host name: %s, actual name: %s", newName, name)
	}
}
