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
	"bytes"
	"fmt"
	"math"
	"net/http"
	"reflect"
	"strings"
)

var (
	IsVraMode = isVraMode()

	IsTest bool
)

//PromptAgreement is asking the user to enter either "y"/"yes" or "n"/"no".
//Returns the user's answer.
func PromptAgreement() bool {
	var answer string
	for {
		fmt.Scanf("%s", &answer)
		answer = strings.ToLower(answer)
		if answer == "yes" || answer == "y" || answer == "no" || answer == "n" {
			break
		}
	}
	if answer == "yes" || answer == "y" {
		return true
	}
	return false
}

//ShortString is trimming string to desired length and adding three dots in the end of it.
//In case len(s) <= desired length, the function returns the same string unmodified.
func ShortString(s string, outputLen int) string {
	if len(s) <= outputLen {
		return s
	}
	return s[:outputLen-3] + "..."
}

// GetTabSeparatedString returns string containing
// the values of all objects passed as parameters
// separated with tabs. This is required for further
// formatting and dynamic aligning.
func GetTabSeparatedString(v ...interface{}) string {
	var buffer bytes.Buffer
	for i := range v {
		buffer.WriteString(fmt.Sprintf("%v", v[i]))
		buffer.WriteString("\t")
	}
	return buffer.String()
}

// Simple implementation for rounding float64 numbers.
// Created because standard math package is missing it.
func MathRound(a float64) float64 {
	if a < 0 {
		return math.Ceil(a - 0.5)
	}
	return math.Floor(a + 0.5)
}

// GetTenant returns the current tenant as string
// loaded from file where it is stored together with the
// auth token.
func GetTenant() string {
	if !IsVraMode {
		return ""
	}
	token := TokenFromFile()
	if !strings.Contains(token, "&") {
		return ""
	}
	return strings.Split(token, "&")[0]
}

// isVraMode is invoked from the exported boolean variable IsVraMode
// It specify if the current user is logged against Admiral in vRA mode.
func isVraMode() bool {
	token, _ := GetAuthToken()
	if strings.Contains(token, "Bearer") {
		return true
	}
	return false
}

// GetMapKeys returns array of values which are the keys
// of map passed as parameter. Nil is returned if the parameter
// is not of type map.
func GetMapKeys(m interface{}) []reflect.Value {
	v := reflect.ValueOf(m)
	if v.Kind() != reflect.Map {
		return nil
	}
	return v.MapKeys()
}

// ValuesToString return string array which holds the string
// representation of every reflect.Value which are passed as parameter.
func ValuesToStrings(v []reflect.Value) []string {
	result := make([]string, 0)
	for _, val := range v {
		if val.String() == "" {
			continue
		}
		result = append(result, val.String())
	}
	return result
}

func IsNilOrEmptyStr(arr []string) bool {
	if arr == nil {
		return true
	}
	if len(arr) < 1 {
		return true
	}
	return false
}

func IsApplicationJson(headers http.Header) bool {
	contentType := headers.Get("Content-Type")
	if contentType == "application/json" {
		return true
	}
	return false
}
