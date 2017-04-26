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

package projects

import (
	"fmt"
	"os"
	"testing"

	"admiral/auth"
	. "admiral/common/utils"
	"admiral/config"
)

var tc = &TestConfig{}

func TestMain(m *testing.M) {
	// Preparing
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

func TestAddUpdateRemoveProject(t *testing.T) {
	// Testing phase 1
	name := "test-project"
	id, err := AddProject(name)
	CheckTestError(err, t)

	// Validating phase 1
	pl := &ProjectList{}
	_, err = pl.FetchProjects()
	CheckTestError(err, t)

	var exist = false
	var actualProject Project
	for _, prj := range pl.Documents {
		if prj.GetID() == id {
			exist = true
			actualProject = prj
		}
	}
	if !exist {
		t.Error("Added project is not found.")
	}

	if actualProject.Name != name {
		t.Errorf("Expected name: %s, actual name: %s", name, actualProject.Name)
	}

	// Testing phase 2
	id, err = RemoveProjectID(id)
	CheckTestError(err, t)

	// Validating phase 2
	exist = false
	pl = &ProjectList{}
	_, err = pl.FetchProjects()
	CheckTestError(err, t)
	for _, prj := range pl.Documents {
		if prj.GetID() == id {
			exist = true
			actualProject = prj
		}
	}
	if exist {
		t.Error("Removed project is found.")
	}
}

func TestAddProjectWithEmptyName(t *testing.T) {
	// Testing
	name := ""
	_, err := AddProject(name)

	// Validating
	if err == nil {
		t.Error("Expected error != nil, got error = nil.")
	}
}

func TestUpdateProject(t *testing.T) {
	// Preparing
	name := "test-project"
	newName := "new-test-project"
	id, err := AddProject(name)
	CheckTestError(err, t)

	// Testing
	_, err = EditProjectID(id, newName)
	CheckTestError(err, t)

	// Validating
	pl := &ProjectList{}
	pl.FetchProjects()
	var exist = false
	var actualProject Project
	for _, prj := range pl.Documents {
		if prj.GetID() == id {
			exist = true
			actualProject = prj
		}
	}
	if !exist {
		t.Error("Updated project is not found.")
	}

	if actualProject.Name != newName {
		t.Errorf("Expected new name: %s, actual new name: %s", newName, actualProject.Name)
	}

	// Cleaning
	_, err = RemoveProjectID(id)
	CheckTestError(err, t)
}
