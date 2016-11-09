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
	"strings"
)

var (
	IsVraMode = isVraMode()
)

//PrintID prints the provided ID as parameter in the format
// "New entity ID: %s\n".
func PrintID(id string) {
	fmt.Printf("New entity ID: %s\n", id)
}

//PromptAgreement is asking the user to enter either "y"/"yes" or "n"/"no".
//Returns the user's answer.
func PromptAgreement() string {
	var answer string
	for {
		fmt.Scanf("%s", &answer)
		answer = strings.ToLower(answer)
		if answer == "yes" || answer == "y" || answer == "no" || answer == "n" {
			break
		}
	}
	return answer
}

//ShortString is trimming string to desired length and adding three dots in the end of it.
//In case len(s) <= desired length, the function returns the same string unmodified.
func ShortString(s string, outputLen int) string {
	if len(s) <= outputLen {
		return s
	}
	return s[:outputLen-3] + "..."
}

func GetFormattedString(v ...interface{}) string {
	var buffer bytes.Buffer
	for i := range v {
		buffer.WriteString(fmt.Sprintf("%v", v[i]))
		buffer.WriteString("\t")
	}
	return buffer.String()
}

func MathRound(a float64) float64 {
	if a < 0 {
		return math.Ceil(a - 0.5)
	}
	return math.Floor(a + 0.5)
}

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

func isVraMode() bool {
	token, _ := GetAuthToken()
	if strings.Contains(token, "Bearer") {
		return true
	}
	return false
}
