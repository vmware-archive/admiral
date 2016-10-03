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

package deplPolicy

import (
	"fmt"
	"os"
	"testing"

	"admiral/config"
	"admiral/loginout"
	. "admiral/testutils"
)

var tc = &TestConfig{}

func TestMain(m *testing.M) {
	var err error
	tc, err = ConfigureTestEnv()
	if err != nil {
		fmt.Println(err)
		os.Exit(-1)
	}
	config.GetCfg()
	loginout.Login(tc.Username, tc.Password, tc.AdmiralAddress)

	code := m.Run()
	os.Exit(code)
}

func TestAddRemoveDeploymentPolicy(t *testing.T) {
	// Testing phase 1
	name := "test-deployment-policy"
	description := "test-description"
	id, err := AddDP(name, description)
	CheckTestError(err, t)

	// Validating phase 1
	dpl := DeploymentPolicyList{}
	dpl.FetchDP()
	exist := false
	actualDP := DeploymentPolicy{}
	for _, dp := range dpl.Documents {
		if dp.GetID() == id {
			exist = true
			actualDP = dp
			break
		}
	}

	if !exist {
		t.Error("Added deployment policy is not found.")
	}

	if actualDP.Name != name {
		t.Errorf("Expected name: %s, actual name: %s", name, actualDP.Name)
	}

	if actualDP.Description != description {
		t.Errorf("Expected description: %s, actual description: %s", description, actualDP.Description)
	}

	// Testing phase 2
	id, err = RemoveDPID(id)
	CheckTestError(err, t)

	// Validating phase 2
	dpl = DeploymentPolicyList{}
	dpl.FetchDP()
	exist = false
	for _, dp := range dpl.Documents {
		if dp.GetID() == id {
			exist = true
			break
		}
	}

	if exist {
		t.Error("Removed deployment policy is found.")
	}
}

func TestUpdateDeploymentPolicy(t *testing.T) {
	// Preparing
	name := "test-deployment-policy"
	newName := "new-test-deployment-policy"
	description := "test-description"
	newDescription := "new-test-description"
	id, err := AddDP(name, description)
	CheckTestError(err, t)

	// Testing
	id, err = EditDPID(id, newName, newDescription)
	CheckTestError(err, t)

	// Validating
	dpl := DeploymentPolicyList{}
	dpl.FetchDP()
	exist := false
	actualDP := DeploymentPolicy{}
	for _, dp := range dpl.Documents {
		if dp.GetID() == id {
			exist = true
			actualDP = dp
			break
		}
	}

	if !exist {
		t.Error("Added deployment policy is not found.")
	}

	if actualDP.Name != newName {
		t.Errorf("Expected new name: %s, actual new name: %s", newName, actualDP.Name)
	}

	if actualDP.Description != newDescription {
		t.Errorf("Expected new description: %s, actual new description: %s", newDescription, actualDP.Description)
	}

	// Cleaning
	_, err = RemoveDPID(id)
	CheckTestError(err, t)
}

func TestAddDeploymentPolicyWithEmptyName(t *testing.T) {
	// Testing phase 1
	name := ""
	description := "test-description"
	_, err := AddDP(name, description)

	// Validating
	if err == nil {
		t.Error("Expected err != nil, got err == nil")
	}
}
