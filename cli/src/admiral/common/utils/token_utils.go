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

package utils

import (
	"io/ioutil"
	"os"
	"strings"
)

var TokenFromFlagVar string

type Error struct {
	Message string `json:"message"`
}

// GetAuthToken returns two strings, the first one is the token,
// the second one specify the source of the token -> flag/env variable/file.
// Order of getting the token is Flag > Env Variable > File.
func GetAuthToken() (string, string) {
	var token string
	token = TokenFromFlag()
	if token != "" {
		return removeTenantFromToken(token), "flag"
	}
	token = TokenFromEnvVar()
	if token != "" {
		return removeTenantFromToken(token), "env variable"
	}
	token = TokenFromFile()
	return removeTenantFromToken(token), "file"
}

// TokenFromFile returns token which is loaded from file.
func TokenFromFile() string {
	token, _ := ioutil.ReadFile(TokenPath())
	return string(token)
}

// TokenFromEnvVar returns token which is from environment variable.
func TokenFromEnvVar() string {
	token := os.Getenv("ADMIRAL_TOKEN")
	return token
}

// TokenFromFlag returns token which is passed as command flag.
func TokenFromFlag() string {
	return TokenFromFlagVar
}

// removeTenantFromToken removes tenant if there is one concatanated with the token.
func removeTenantFromToken(token string) string {
	if !strings.Contains(token, "&") {
		return token
	}
	return strings.Split(token, "&")[1]
}
