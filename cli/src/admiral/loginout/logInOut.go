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
	"net/http"
	"os"
	"strings"
	"time"

	"admiral/client"
	"admiral/config"
	"admiral/utils"
)

type LogInOut struct {
	RequestType string `json:"requestType"`
}

func Login(username, password, configUrl string) string {
	fail := "Login failed."
	success := "Login successful."
	if configUrl != "" {
		config.URL = configUrl
		config.SetProperty("Url", configUrl)
	}
	os.Remove(utils.TokenPath())
	url := config.URL + "/core/authn/basic"
	login := &LogInOut{
		RequestType: "LOGIN",
	}
	jsonBody, _ := json.Marshal(login)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	req.SetBasicAuth(strings.TrimSpace(username), strings.TrimSpace(password))
	resp, _, _ := client.ProcessRequest(req)
	if resp == nil {
		return fail
	}
	token := resp.Header.Get("x-xenon-auth-token")
	if token == "" {
		return fail
	}
	if utils.Verbose {
		fmt.Printf("%s: %s\n", "x-xenon-aut-token", token)
	}
	utils.MkCliDir()
	tokenFile, err := os.Create(utils.TokenPath())

	utils.CheckFile(err)
	tokenFile.Write([]byte(token))
	tokenFile.Close()
	return success
}

func Logout() {
	url := config.URL + "/core/authn/basic"
	logout := &LogInOut{
		RequestType: "LOGOOUT",
	}
	jsonBody, _ := json.Marshal(logout)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))

	_, _, respErr := client.ProcessRequest(req)
	utils.CheckResponse(respErr, url)

	err := os.Remove(utils.TokenPath())

	utils.CheckFile(err)

	fmt.Println("Logged out.")

}

func GetInfo() string {
	var buffer bytes.Buffer
	token, tokenSource := utils.GetAuthToken()
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
	buffer.WriteString("Token: " + token + "\n")
	buffer.WriteString("Token source: " + strings.Title(tokenSource))
	return buffer.String()
}

func Loginvra(username, password, tenant, urlF string) string {
	if tenant == "" || urlF == "" {
		return "Tenant and/or url not provided."
	}
	login := &RequestLoginVRA{
		Username: username,
		Password: password,
		Tenant:   tenant,
	}
	os.Remove(utils.TokenPath())
	if !strings.HasSuffix(urlF, "/container-service/api") {
		config.URL = urlF + "/container-service/api"
		config.SetProperty("Url", config.URL)
	}
	url := urlF + "/identity/api/tokens"

	jsonBody, err := json.Marshal(login)
	utils.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return respErr.Error()
	}
	respLogin := &ResponseLoginVRA{}
	err = json.Unmarshal(respBody, &respLogin)
	utils.MkCliDir()
	tokenFile, err := os.Create(utils.TokenPath())

	utils.CheckFile(err)
	tokenFile.Write([]byte("Bearer " + respLogin.Id))
	tokenFile.Close()
	return "Login successful."
}

type RequestLoginVRA struct {
	Username string `json:"username"`
	Password string `json:"password"`
	Tenant   string `json:"tenant"`
}

type ResponseLoginVRA struct {
	Expires string `json:"expires"`
	Id      string `json:"id"`
	Tenant  string `json:"tenant"`
}
