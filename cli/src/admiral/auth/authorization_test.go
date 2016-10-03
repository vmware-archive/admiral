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

package auth

import (
	"os"
	"testing"
)

func TestGetAuthTokenPriority(t *testing.T) {
	// Preparing
	TokenFromFlagVar = "flag_test_token"
	os.Setenv("ADMIRAL_TOKEN", "env_test_token")
	expectedSource := "flag"

	// Testing
	token, actualSource := GetAuthToken()

	// Validating
	if token != TokenFromFlagVar {
		t.Errorf("Expected token: %s, got %s", TokenFromFlagVar, token)
	}

	if actualSource != expectedSource {
		t.Errorf("Expected token from: %s, got from: %s", expectedSource, actualSource)
	}
}
