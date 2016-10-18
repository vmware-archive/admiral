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

package credentials

import (
	"bufio"
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"strings"
	"syscall"

	"admiral/client"
	"admiral/config"
	"admiral/functions"
	"admiral/properties"

	"golang.org/x/crypto/ssh/terminal"
)

var (
	DuplicateNamesError      = errors.New("Credentials with duplicate name found, provide ID to remove specific credentials.")
	CredentialsNotFoundError = errors.New("Credentials not found.")
)

type CustomProperties struct {
	AuthCredentialsName string `json:"__authCredentialsName"`
}

type Credentials struct {
	UserEmail        string             `json:"userEmail"`
	PrivateKey       string             `json:"privateKey,omitempty"`
	PublicKey        string             `json:"publicKey,omitempty"`
	Type             string             `json:"type,omitempty"`
	CustomProperties map[string]*string `json:"customProperties"`
	DocumentSelfLink string             `json:"documentSelfLink,omitempty"`
}

func (c *Credentials) GetID() string {
	return strings.Replace(c.DocumentSelfLink, "/core/auth/credentials/", "", -1)
}

func (c *Credentials) GetName() string {
	var checker string
	if c.CustomProperties["__authCredentialsName"] == nil {
		checker = "n/a"
	} else {
		checker = strings.TrimSpace(*c.CustomProperties["__authCredentialsName"])
	}
	return checker
}

type ListCredentials struct {
	Documents     map[string]Credentials `json:"documents"`
	DocumentLinks []string               `json:"documentLinks"`
}

//FetchCredentials fetches all credentials. It return the count
//of fetched credentials.
func (lc *ListCredentials) FetchCredentials() (int, error) {
	url := config.URL + "/core/auth/credentials?expand&$filter=customProperties/scope%20ne%20%27SYSTEM%27"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, lc)
	functions.CheckJson(err)
	return len(lc.Documents), nil
}

//Print prints already fetched credentials.
func (lc *ListCredentials) GetOutputString() string {
	var buffer bytes.Buffer
	buffer.WriteString("ID\tNAME\tTYPE")
	buffer.WriteString("\n")
	for _, link := range lc.DocumentLinks {
		cred := lc.Documents[link]
		output := functions.GetFormattedString(cred.GetID(), cred.GetName(), cred.Type)
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

type AddUserCredentials struct {
	CustomProperties map[string]*string `json:"customProperties,omitempty"`
	PrivateKey       string             `json:"privateKey"`
	Type             string             `json:"type"`
	UserEmail        string             `json:"userEmail"`
}

type AddCertCredentials struct {
	CustomProperties map[string]*string `json:"customProperties,eomitempty"`
	PrivateKey       string             `json:"privateKey"`
	Type             string             `json:"type"`
	PublicKey        string             `json:"publicKey"`
}

//String returns string containing credentials name.
func (c *CustomProperties) String() string {
	return c.AuthCredentialsName
}

//Function that maps the names of credentials with links of credentials with the same name.
func (lc *ListCredentials) GetMapNamesToLinks() map[string][]string {
	mappedNames := make(map[string][]string)
	for key, val := range lc.Documents {
		if _, ok := mappedNames[*val.CustomProperties["__authCredentialsName"]]; !ok {
			mappedNames[*val.CustomProperties["__authCredentialsName"]] = make([]string, 0)
		}
		mappedNames[*val.CustomProperties["__authCredentialsName"]] = append(mappedNames[*val.CustomProperties["__authCredentialsName"]], key)
	}
	return mappedNames
}

//GetCredentialsLinks takes string containing desired name of credentials.
//It fetches all the credentials and iterate over them looking for matching names.
//Returns string array containing self links of credentials with the same name
//as the one provided as parameter.
func GetCredentialsLinks(name string) []string {
	lc := &ListCredentials{}
	lc.FetchCredentials()
	links := make([]string, 0)
	for link, cred := range lc.Documents {
		currentName := cred.CustomProperties["__authCredentialsName"]
		if currentName != nil && name == *currentName {
			links = append(links, link)
		}
	}
	return links
}

//AddByUsername is adding new credentials. Parameters are the name
//of the credentials, the username, the password and array of custom properties.
//For more information about custom properties format and how they are being parsed,
//take a look at "properties" package.
func AddByUsername(name, userName, passWord string,
	custProps []string) (string, error) {
	url := config.URL + "/core/auth/credentials"
	reader := bufio.NewReader(os.Stdin)

	if userName == "" {
		fmt.Println("Enter username:")
		userName, _ = reader.ReadString('\n')
	}

	if passWord == "" {
		fmt.Println("Enter password:")
		bytePassword, err := terminal.ReadPassword(int(syscall.Stdin))
		passWord = string(bytePassword)
		if err != nil {
			return "", err
		}
	}

	//nameProp := CustomProperties{
	//	AuthCredentialsName: strings.TrimSpace(name),
	//}
	cp := properties.ParseCustomProperties(custProps)
	if cp == nil {
		cp = make(map[string]*string, 0)
	}
	cp = properties.AddCredentialsName(name, cp)

	user := AddUserCredentials{
		Type:             "Password",
		UserEmail:        strings.TrimSpace(userName),
		PrivateKey:       strings.TrimSpace(passWord),
		CustomProperties: cp,
	}

	jsonBody, err := json.Marshal(user)
	functions.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	creds := &Credentials{}
	err = json.Unmarshal(respBody, creds)
	functions.CheckJson(err)
	return creds.GetID(), nil

}

//AddByCert is adding new credentials. Parameters are the name
//of the credentials, path to file to the public certificate,
//path to file to the private certificate and array of custom properties.
//For more information about custom properties format and how they are being parsed,
//take a look at "properties" package.
func AddByCert(name, publicCert, privateCert string,
	custProps []string) (string, error) {
	url := config.URL + "/core/auth/credentials"
	bytePrivate, err := ioutil.ReadFile(privateCert)
	if err != nil {
		return "", err
	}
	privateKey := string(bytePrivate)
	bytePublic, err := ioutil.ReadFile(publicCert)
	if err != nil {
		return "", err
	}
	publicKey := string(bytePublic)
	cp := properties.ParseCustomProperties(custProps)
	if cp == nil {
		cp = make(map[string]*string, 0)
	}
	cp = properties.AddCredentialsName(name, cp)

	cert := AddCertCredentials{
		CustomProperties: cp,
		PrivateKey:       privateKey,
		PublicKey:        publicKey,
		Type:             "PublicKey",
	}
	jsonBody, err := json.Marshal(cert)
	functions.CheckJson(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	creds := &Credentials{}
	err = json.Unmarshal(respBody, creds)
	functions.CheckJson(err)
	return creds.GetID(), nil
}

//RemoveCredentials removes credentials by name that is passed as parameter.
//Returns the ID of the removed credentials and error which is != nil if
//none or more that one credentials have the same name or if the response code
//is different from 200.
func RemoveCredentials(name string) (string, error) {
	links := GetCredentialsLinks(name)
	if len(links) < 1 {
		return "", CredentialsNotFoundError
	}

	if len(links) > 1 {
		return "", DuplicateNamesError
	}
	return RemoveCredentialsID(links[0])
}

//RemoveCredentialsID removes credentials by ID that is passed as parameter.
//Returns the ID of removed credentials and error which is != nil if
//the response code is different from 200.
func RemoveCredentialsID(id string) (string, error) {
	link := functions.CreateResLinkForCredentials(id)
	url := config.URL + link
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil
}

//EditCredentials edits credentials by name that is passed as parameter.
//Other parameters are the desired properties to edit, pass empty string in case
//you don't want to modify the property.
//Returns the ID of the edited credentials and error which is != nil if
//none or more that one credentials have the same name or if the response code
//is different from 200.
func EditCredetials(credName, publicCert, privateCert, userName, passWord string) (string, error) {
	links := GetCredentialsLinks(credName)
	if len(links) < 1 {
		return "", CredentialsNotFoundError
	}
	if len(links) > 1 {
		return "", DuplicateNamesError

	}
	return EditCredetialsID(links[0], publicCert, privateCert, userName, passWord)
}

//EditCredentialsID edits credentials by ID that is passed as parameter.
//Other parameters are the desired properties to edit, pass empty string in case
//you don't want to modify the property.
//Returns the ID of the edited credentials and error which is != nil if
//the response code is different from 200.
func EditCredetialsID(id, publicCert, privateCert, userName, passWord string) (string, error) {
	url := config.URL + functions.CreateResLinkForCredentials(id)
	var cred interface{}
	if publicCert != "" && privateCert != "" {
		publicKey, err := ioutil.ReadFile(publicCert)
		functions.CheckFile(err)
		privateKey, err := ioutil.ReadFile(privateCert)
		functions.CheckFile(err)
		cred = &AddCertCredentials{
			PublicKey:  string(publicKey),
			PrivateKey: string(privateKey),
			Type:       "PublicKey",
		}
	} else if userName != "" && passWord != "" {
		cred = &AddUserCredentials{
			PrivateKey: passWord,
			UserEmail:  userName,
			Type:       "Password",
		}
	}
	jsonBody, err := json.Marshal(cred)
	functions.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	creds := &Credentials{}
	err = json.Unmarshal(respBody, creds)
	functions.CheckJson(err)
	return creds.GetID(), nil
}

//GetPublicCustomProperties returns map containing the public
//custom properties of the credentials. As private properties are considered
//these one starting with "__". Note that keys are strings, but values are
//pointer to strings.
func GetPublicCustomProperties(id string) (map[string]*string, error) {
	custProps, err := GetCustomProperties(id)
	if custProps == nil {
		return nil, err
	}
	publicCustProps := make(map[string]*string)
	for key, val := range custProps {
		if len(key) > 2 {
			if key[0:2] == "__" {
				continue
			}
		}
		publicCustProps[key] = val
	}
	return publicCustProps, nil
}

//GetCustomProperties returns map containing the all
//custom properties of the credentials.  Note that keys are strings,
//but values are pointer to strings.
func GetCustomProperties(id string) (map[string]*string, error) {
	link := functions.CreateResLinkForCredentials(id)
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return nil, respErr
	}
	credentials := &Credentials{}
	err := json.Unmarshal(respBody, credentials)
	functions.CheckJson(err)
	return credentials.CustomProperties, nil
}

//AddCustomProperties adds custom properties to the credentials. The parameters
//that functions takes are the ID for the credentials, and arrays of keys and values.
//Note: If length of the keys and values arrays is different, properties are added
//matching the same indexes from both arrays. That also means if the one array is longer
//than the other, it's left elements are ignored.
func AddCustomProperties(id string, keys, vals []string) error {
	link := functions.CreateResLinkForCredentials(id)
	url := config.URL + link
	var lowerLen []string
	if len(keys) > len(vals) {
		lowerLen = vals
	} else {
		lowerLen = keys
	}
	custProps := make(map[string]*string)
	for i, _ := range lowerLen {
		custProps[keys[i]] = &vals[i]
	}

	credentials := &Credentials{
		CustomProperties: custProps,
	}
	jsonBody, err := json.Marshal(credentials)
	functions.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return respErr
	}
	return nil
}

//RemoveCustomProperties removes properties of credentials.
//The function takes as parameter the ID of the credentials
//and array of keys to be removed.
func RemoveCustomProperties(id string, keys []string) error {
	link := functions.CreateResLinkForCredentials(id)
	url := config.URL + link
	custProps := make(map[string]*string)
	for i := range keys {
		custProps[keys[i]] = nil
	}

	credentials := &Credentials{
		CustomProperties: custProps,
	}
	jsonBody, err := json.Marshal(credentials)
	functions.CheckJson(err)
	req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
	_, _, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return respErr
	}
	return nil
}
