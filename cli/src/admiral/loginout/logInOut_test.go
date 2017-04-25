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

package loginout

import (
	"fmt"
	"os"
	"testing"

	"admiral/config"
	. "admiral/testutils"
	"admiral/utils"
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

	code := m.Run()
	os.Exit(code)
}

func TestLoginCorrect(t *testing.T) {
	// Testing
	message, _ := Login(tc.Username, tc.Password, tc.AdmiralAddress)

	// Validating
	if message != "Login successful." {
		t.Errorf("Expected message = Login successful. got %s", message)
	}
}

func TestLoginIncorrect(t *testing.T) {
	// Testing
	_, err := Login("invalidUsername", "invalidPass", tc.AdmiralAddress)

	// Validating
	if err != LoginFailedError {
		t.Errorf("Expected error: %s. got %s", LoginFailedError, err)
	}
}
