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

package client

import (
	"bytes"
	"encoding/json"
	"io/ioutil"
	"net"
	"net/http"
	"time"

	"admiral/auth"
	"admiral/config"
	"admiral/functions"
	"errors"
)

type ResponseError struct {
	Message string `json:"message"`
}

var timeoutDuration = time.Second * time.Duration(config.CLIENT_TIMEOUT)

var netTransport = &http.Transport{
	Dial: (&net.Dialer{
		Timeout: timeoutDuration,
	}).Dial,
	TLSHandshakeTimeout: timeoutDuration,
	Proxy:               http.ProxyFromEnvironment,
}

var NetClient = &http.Client{
	Timeout:   timeoutDuration,
	Transport: netTransport,
}

//ProcessRequest is used for common requests. As parameter is taking
//request with only set method, url and body if there is one, then it handles
//things like adding auth token, sending the requests and checks if verbose boolean
//is true to print both requests and responses. Returns the response and the
//response body as byte array.
func ProcessRequest(req *http.Request) (*http.Response, []byte, error) {
	token, from := auth.GetAuthToken()
	functions.CheckVerboseRequest(req)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("x-xenon-auth-token", token)
	resp, err := NetClient.Do(req)
	functions.CheckResponse(err)
	functions.CheckVerboseResponse(resp)

	if err = CheckResponseError(resp, from); err != nil {
		return nil, nil, err
	}

	respBody, err := ioutil.ReadAll(resp.Body)
	functions.CheckResponse(err)
	defer resp.Body.Close()
	return resp, respBody, nil
}

//CheckResponseError checks if the response code is 4xx and then prints any error message,
//or if the message is "forbidden", prints that there is authentication problem.
func CheckResponseError(resp *http.Response, tokenFrom string) error {
	if resp.StatusCode >= 400 && resp.StatusCode <= 500 {
		body, err := ioutil.ReadAll(resp.Body)
		functions.CheckJson(err)
		//Create 2 new readers.
		//rdrToUse will be modified. rdrToSet will stay the same and set back to the request.
		rdrToUse := ioutil.NopCloser(bytes.NewBuffer(body))
		rdrToSet := ioutil.NopCloser(bytes.NewBuffer(body))
		respBody, err := ioutil.ReadAll(rdrToUse)
		//Set unmodified reader.
		resp.Body = rdrToSet

		message := &ResponseError{}
		functions.CheckResponse(err)
		err = json.Unmarshal(respBody, message)
		functions.CheckJson(err)
		if message.Message == "forbidden" {
			return errors.New("Authorization error. Token used from " + tokenFrom)
		}
		return errors.New(message.Message)
	}
	return nil
}
