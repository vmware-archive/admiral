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

package templates

import (
	"admiral/config"
	"admiral/loginout"
	. "admiral/testutils"
	"fmt"
	"os"
	"testing"
)

var tc = &TestConfig{}

func TestMain(m *testing.M) {
	// Preparing
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

func TestImportRemoveTemplate(t *testing.T) {
	// Testing phase 1
	id, err := Import("../testdata/wordpress.yaml")
	CheckTestError(err, t)

	// Validating phase 1
	tl := TemplatesList{}
	tl.FetchTemplates("")
	var exist = false
	for _, tmpl := range tl.Results {
		if tmpl.GetID() == id {
			exist = true
		}
	}

	if !exist {
		t.Error("Imported template not found.")
	}

	// Testing phase 2
	id, err = RemoveTemplateID(id)
	CheckTestError(err, t)

	// Validating phase 2
	tl = TemplatesList{}
	tl.FetchTemplates("")
	exist = false
	for _, tmpl := range tl.Results {
		if tmpl.GetID() == id {
			exist = true
		}
	}

	if exist {
		t.Error("Removed template is found.")
	}
}
