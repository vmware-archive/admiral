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

package images

import (
	"fmt"
	"os"
	"testing"

	"admiral/config"
	"admiral/loginout"
	. "admiral/testutils"
	"admiral/utils"
	"sort"
)

var tc = &TestConfig{}

func TestMain(m *testing.M) {
	var err error
	tc, err = ConfigureTestEnv()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	utils.IsTest = true
	config.GetCfgForTests()
	loginout.Login(tc.Username, tc.Password, tc.AdmiralAddress)

	code := m.Run()
	os.Exit(code)
}

func TestQueryImagesAndSort(t *testing.T) {
	// Testing
	imageName := "ubuntu"
	il := &ImagesList{}
	_, err := il.QueryImages(imageName)
	CheckTestError(err, t)

	sort.Sort(ImageSorter(il.Results))

	// Validating
	if len(il.Results) < 1 {
		t.Error("Expected length >= 1, actual length < 1")
		t.FailNow()
	}

	if il.Results[0].GetShortName() != imageName {
		t.Errorf("Expected short name: %s, actual short name: %s", imageName, il.Results[0].GetShortName())
	}
}
