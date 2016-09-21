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
	"os"
	"testing"
)

//Check for error raised by response.
//Print message.
//Currently panic too used for debugging.
func CheckResponse(err error) {
	if err != nil {
		fmt.Println("Response error occured.")
		fmt.Println(err.Error())
		panic(err.Error())
		os.Exit(-2)
	}
}

//Check for error raised by reading/writing json.
//Print message.
//Currently panic too used for debugging.
func CheckJson(err error) {
	if err != nil {
		fmt.Println("Json error when reading and/or writing.")
		fmt.Println(err.Error())
		panic(err.Error())
		os.Exit(-2)
	}
}

//Check for error raised by operations with files.
//Print message.
//Currently panic too used for debugging.
func CheckFile(err error) {
	if err != nil {
		fmt.Println("Error on read/write file.")
		fmt.Println(err.Error())
		os.Exit(-2)
	}
}

func CheckParse(err error) {
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(-2)
	}
}

func CheckTestError(err error, t *testing.T) {
	if err != nil {
		t.Error(err)
		t.FailNow()
	}
}
