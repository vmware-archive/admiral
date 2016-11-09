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
	"encoding/json"
	"fmt"
	"io/ioutil"
	"os"
	"strings"
)

var TokenFromFlagVar string

type Error struct {
	Message string `json:"message"`
}

//Function to get current auth token from the temp file.
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

//Function that verify after every single request if the user is still authorized.
//Returns true if it's authorized and false if it's not authorized.
func IsAuthorized(respBody []byte, tokenFrom string) bool {
	authCheck := &Error{}
	err := json.Unmarshal(respBody, authCheck)
	if authCheck.Message == "forbidden" && err == nil {
		fmt.Println("Authorization error.")
		fmt.Println("Check if you are logged in.")
		fmt.Println("Token used from " + tokenFrom)
		return false
	}
	return true
}

func TokenFromFile() string {
	token, _ := ioutil.ReadFile(TokenPath())
	return string(token)
}

func TokenFromEnvVar() string {
	token := os.Getenv("ADMIRAL_TOKEN")
	return token
}

func TokenFromFlag() string {
	return TokenFromFlagVar
}

func removeTenantFromToken(token string) string {
	if !strings.Contains(token, "&") {
		return token
	}
	return strings.Split(token, "&")[1]
}
