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

package functions

import (
	"fmt"
	"strings"
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
