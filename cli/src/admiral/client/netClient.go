package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net"
	"net/http"
	"os"
	"time"

	"admiral/auth"
	"admiral/functions"
)

type ResponseError struct {
	Message string `json:"message"`
}

var netTransport = &http.Transport{
	Dial: (&net.Dialer{
		Timeout: 10 * time.Second,
	}).Dial,
	TLSHandshakeTimeout: 10 * time.Second,
	Proxy:               http.ProxyFromEnvironment,
}

var NetClient = &http.Client{
	Timeout:   time.Second * 10,
	Transport: netTransport,
}

//ProcessRequest is used for common requests. As parameter is taking
//request with only set method, url and body if there is one, then it handles
//things like adding auth token, sending the requests and checks if verbose boolean
//is true to print both requests and responses. Returns the response and the
//response body as byte array.
func ProcessRequest(req *http.Request) (resp *http.Response, respBody []byte) {
	token, from := auth.GetAuthToken()
	functions.CheckVerboseRequest(req)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("x-xenon-auth-token", token)
	resp, err := NetClient.Do(req)
	functions.CheckResponse(err)
	functions.CheckVerboseResponse(resp)

	if CheckResponseError(resp, from) {
		os.Exit(1)
	}

	respBody, err = ioutil.ReadAll(resp.Body)
	functions.CheckResponse(err)
	defer resp.Body.Close()
	return
}

//CheckResponseError checks if the response code is 4xx and then prints any error message,
//or if the message is "forbidden", prints that there is authentication problem.
func CheckResponseError(resp *http.Response, tokenFrom string) bool {
	if resp.StatusCode >= 400 && resp.StatusCode <= 417 {
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
			fmt.Println("Authorization error.\nCheck if you are logged in.")
			fmt.Println("Token used from " + tokenFrom)
			os.Exit(0)
			return true
		}
		fmt.Println(message.Message)
		return true
	} else if resp.StatusCode == 500 {
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
			fmt.Println("Authorization error.\nCheck if you are logged in.")
			fmt.Println("Token used from " + tokenFrom)
			os.Exit(0)
			return true
		}
		fmt.Println(message.Message)
		return true
	}
	return false
}
