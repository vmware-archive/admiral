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

package loginout

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"strings"
	"time"

	"admiral/auth"
	"admiral/client"
	"admiral/config"
	"admiral/functions"
	"admiral/paths"
)

type LogInOut struct {
	RequestType string `json:"requestType"`
}

func Login(username, password, configUrl string) string {
	if configUrl != "" {
		config.URL = configUrl
		config.SetProperty("Url", configUrl)
	}
	url := config.URL + "/core/authn/basic"
	login := &LogInOut{
		RequestType: "LOGIN",
	}
	jsonBody, _ := json.Marshal(login)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	functions.CheckVerboseRequest(req)
	req.SetBasicAuth(strings.TrimSpace(username), strings.TrimSpace(password))
	resp, err := client.NetClient.Do(req)
	functions.CheckResponse(err)

	functions.CheckVerboseResponse(resp)
	token := resp.Header.Get("x-xenon-auth-token")
	if token == "" {
		fmt.Println("Login failed.")
		return ""
	}
	if functions.Verbose {
		fmt.Printf("%s: %s\n", "x-xenon-aut-token", token)
	}
	paths.MkCliDir()
	tokenFile, err := os.Create(paths.TokenPath())

	functions.CheckFile(err)
	tokenFile.Write([]byte(token))
	tokenFile.Close()
	fmt.Println("Login successful.")
	return token
}

func Logout() {
	url := config.URL + "/core/authn/basic"
	logout := &LogInOut{
		RequestType: "LOGOOUT",
	}
	jsonBody, _ := json.Marshal(logout)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	functions.CheckVerboseRequest(req)

	_, err := client.NetClient.Do(req)

	functions.CheckResponse(err)

	err = os.Remove(paths.TokenPath())

	functions.CheckFile(err)

	fmt.Println("Logged out.")

}

func GetInfo() string {
	var buffer bytes.Buffer
	token, _ := auth.GetAuthToken()
	if token == "" {
		return "Not logged in, no token found."
	}
	tokenArr := strings.Split(token, ".")
	if len(tokenArr) < 3 || len(tokenArr) > 3 {
		return ""
	}
	type TokenInfo struct {
		Sub string `json:"sub"`
		Exp int64  `json:"exp"`
	}
	ti := &TokenInfo{}
	info, err := base64.RawStdEncoding.DecodeString(tokenArr[1])
	if err != nil {
		return err.Error()
	}
	json.Unmarshal(info, ti)
	user := strings.Replace(ti.Sub, "/core/authz/users/", "", -1)
	expDate := time.Unix(0, ti.Exp*int64(time.Microsecond))
	buffer.WriteString("User: " + user + "\n")
	buffer.WriteString("Expiration Date: " + expDate.Format("2006.01.02 15:04:05") + "\n")
	buffer.WriteString("Token: " + token)
	return buffer.String()
}

func Loginvra(username, password, tenant string) {
	login := &RequestLoginVRA{
		Username: username,
		Password: password,
		Tenant:   tenant,
	}
	url := config.URL + "/identity/api/tokens"
	jsonBody, err := json.Marshal(login)
	functions.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	resp, err := client.NetClient.Do(req)
	functions.CheckResponse(err)
	respLogin := &ResponseLoginVRA{}
	respBody, _ := ioutil.ReadAll(resp.Body)
	err = json.Unmarshal(respBody, &respLogin)

}

type RequestLoginVRA struct {
	Username string `json:"username"`
	Password string `json:"passowrd"`
	Tenant   string `json:"tenant"`
}

type ResponseLoginVRA struct {
	Expires string `json:"expires"`
	Id      string `json:"id"`
	Tenant  string `json:"tenant"`
}
