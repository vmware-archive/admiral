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
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
)

var Verbose bool
var Quiet bool

//If verbose flag is provided, will print the request send to the API.
func CheckVerboseRequest(req *http.Request) {
	if !Verbose || Quiet || req.Body == nil || !IsApplicationJson(req.Header) {
		return
	}

	//Read
	buf, err := ioutil.ReadAll(req.Body)
	CheckBlockingError(err)
	//Create 2 new readers.
	//rdrToUse will be modified. rdrToSet will stay the same and set back to the request.
	rdrToUse := ioutil.NopCloser(bytes.NewBuffer(buf))
	rdrToSet := ioutil.NopCloser(bytes.NewBuffer(buf))

	//Print request.
	body, err := ioutil.ReadAll(rdrToUse)

	requestMap := make(map[string]interface{})
	err = json.Unmarshal(body, &requestMap)
	CheckBlockingError(err)
	removeSensitiveFields(&requestMap)

	body, err = json.MarshalIndent(requestMap, "", "    ")

	CheckBlockingError(err)
	fmt.Printf("%s %s\n", req.Method, req.URL)
	fmt.Println(string(body))

	//Set unmodified reader.
	req.Body = rdrToSet

}

//If verbose flag is provided, will print the response send from the API.
func CheckVerboseResponse(resp *http.Response) {
	if !Verbose || resp == nil || Quiet || resp.Body == nil || !IsApplicationJson(resp.Header) {
		return
	}
	//Read
	buf, err := ioutil.ReadAll(resp.Body)
	defer resp.Body.Close()
	CheckBlockingError(err)

	//Create 2 new readers.
	//rdrToUse will be modified. rdrToSet will stay the same and set back to the request.
	rdrToUse := ioutil.NopCloser(bytes.NewBuffer(buf))
	rdrToSet := ioutil.NopCloser(bytes.NewBuffer(buf))

	jsonBody, err := ioutil.ReadAll(rdrToUse)
	CheckBlockingError(err)
	if len(jsonBody) < 1 {
		fmt.Printf("Response status: %s\n", resp.Status)
		resp.Body = rdrToSet
		return
	}
	fmt.Printf("Response status: %s\n", resp.Status)
	var indentBody bytes.Buffer
	err = json.Indent(&indentBody, jsonBody, "", "    ")
	fmt.Println(string(indentBody.Bytes()))
	resp.Body = rdrToSet
}

func removeSensitiveFields(requestMap *map[string]interface{}) {
	delete(*requestMap, "password")
}
