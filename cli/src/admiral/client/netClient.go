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
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io/ioutil"
	"net"
	"net/http"
	"os"
	"strings"
	"time"

	"admiral/config"
	"admiral/utils"
)

const (
	HttpsDefaultPort string = ":443"
	TimeoutSeconds   int64  = 60
)

var (
	Code401Error      = errors.New("HTTP Status 401 - Authentication required")
	Code403Error      = errors.New("HTTP Status 403 - Forbidden")
	Code404Error      = errors.New("HTTP Status 404 - Not found.")
	NullResponseError = errors.New("Response from the server is null.")

	customTimeout int
)

type AuthorizationError struct {
	message, tokenSource string
}

func (ae AuthorizationError) Error() string {
	return fmt.Sprintf(ae.message, ae.tokenSource)
}

func NewAuthorizationError(tokenSource string) AuthorizationError {
	authErr := AuthorizationError{
		message:     "Authorization error. Token used from %s.",
		tokenSource: tokenSource,
	}
	return authErr
}

type ResponseError struct {
	Message string `json:"message"`
}

func (re *ResponseError) Error() string {
	return re.Message
}

var (
	skipSSLVerification bool

	netClient    *http.Client
	netClientErr error
)

//ProcessRequest is used for common requests. As parameter is taking
//request with only set method, url and body if there is one, then it handles
//things like adding auth token, sending the requests and checks if verbose boolean
//is true to print both requests and responses. Returns the response and the
//response body as byte array.
func ProcessRequest(req *http.Request) (*http.Response, []byte, error) {
	token, from := utils.GetAuthToken()
	if netClient == nil {
		validateConnection(req.URL)
		netClient, netClientErr = buildHttpClient()
		if netClientErr != nil {
			return nil, nil, netClientErr
		}
	}

	setReqHeaders(req, token)
	utils.CheckVerboseRequest(req)

	resp, err := netClient.Do(req)
	if err != nil {
		return nil, nil, err
	}
	admiralHostUrl := req.URL.Scheme + "://" + req.URL.Host
	utils.CheckResponse(err, admiralHostUrl)
	utils.CheckVerboseResponse(resp)
	if err = CheckResponseError(resp, from); err != nil {
		return nil, nil, err
	}

	respBody, err := ioutil.ReadAll(resp.Body)
	resp.Body.Close()
	return resp, respBody, nil
}

func ProcessRequestUntilNotFound(req *http.Request) error {
	token, _ := utils.GetAuthToken()
	if netClient == nil {
		validateConnection(req.URL)
		netClient, netClientErr = buildHttpClient()
		if netClientErr != nil {
			return netClientErr
		}
	}

	setReqHeaders(req, token)

	start := time.Now()
	for {
		elapsed := time.Now().Sub(start)
		if elapsed.Seconds() > float64(TimeoutSeconds) {
			return errors.New("Waiting for 404 status code timed out.")
		}
		resp, err := netClient.Do(req)
		if err != nil {
			return err
		}

		if resp.StatusCode == http.StatusNotFound {
			return nil
		}
		time.Sleep(3 * time.Second)
	}
}

//CheckResponseError checks if the response code is 4xx and then prints any error message,
//or if the message is "forbidden", prints that there is authentication problem.
func CheckResponseError(resp *http.Response, tokenFrom string) error {
	if resp == nil {
		return NullResponseError
	}
	if resp.StatusCode >= 400 && resp.StatusCode <= 500 {
		body, err := ioutil.ReadAll(resp.Body)
		defer resp.Body.Close()
		//Create 2 new readers.
		//rdrToUse will be modified. rdrToSet will stay the same and set back to the request.
		rdrToUse := ioutil.NopCloser(bytes.NewBuffer(body))
		rdrToSet := ioutil.NopCloser(bytes.NewBuffer(body))
		respBody, err := ioutil.ReadAll(rdrToUse)
		//Set unmodified reader.
		resp.Body = rdrToSet

		message := &ResponseError{}
		err = json.Unmarshal(respBody, message)
		if err != nil {
			return getResponseError(resp.StatusCode)
		}
		if message.Message == "forbidden" {
			return NewAuthorizationError(tokenFrom)
		}
		if message.Message == "" {
			return errors.New("Connection error " + resp.Status)
		}
		return errors.New(message.Message)
	}
	return nil
}

func SetCustomTimeout(timeout int) {
	if customTimeout < 1 {
		return
	}
	customTimeout = timeout
}

// setReqHeaders sets most common request headers.
func setReqHeaders(req *http.Request, token string) {
	if req.Header.Get("Content-Type") == "" {
		req.Header.Set("Content-Type", "application/json")
	}
	if strings.HasPrefix(token, "Bearer") {
		req.Header.Set("Authorization", token)
	} else {
		req.Header.Set("x-xenon-auth-token", token)
	}
	req.Header.Set("Accept", "application/json")
}

//buildHttpClient is setting up CA pool and adding this pool
//to the http client configuration along with other configurations.
func buildHttpClient() (*http.Client, error) {
	caCertPool, err := setupCAPool()
	if err != nil {
		return nil, err
	}

	var tlsClientConfig = &tls.Config{
		RootCAs:            caCertPool,
		InsecureSkipVerify: skipSSLVerification,
	}

	var timeoutDuration time.Duration
	if customTimeout != 0 {
		timeoutDuration = time.Duration(customTimeout) * time.Second
	} else {
		timeoutDuration = time.Second * time.Duration(config.CLIENT_TIMEOUT)
	}

	var netTransport = &http.Transport{
		Dial: (&net.Dialer{
			Timeout: timeoutDuration,
		}).Dial,
		TLSHandshakeTimeout: timeoutDuration,
		Proxy:               http.ProxyFromEnvironment,
		TLSClientConfig:     tlsClientConfig,
	}

	netClient := &http.Client{
		Timeout:   timeoutDuration,
		Transport: netTransport,
	}

	return netClient, nil
}

// promptAllCerts makes tls dial to fetch server certificates,
// and then prompts all of them to the user.
func promptAllCerts(url string) bool {
	url = urlAppendDefaultPort(url)
	conn, err := tls.Dial("tcp", url, &tls.Config{InsecureSkipVerify: true})
	if err != nil {
		return false
	}
	cs := conn.ConnectionState()
	answer := false
	for _, cert := range cs.PeerCertificates {
		if promptCertAgreement(cert) {
			cert.IsCA = true
			saveTrustedCert(cert)
			answer = true
		} else {
			answer = false
			return answer
		}
	}
	return answer
}

// saveTrustedCert takes x509 certificate as parameter, encode it,
// and finally saves it to file where other user trusted certificates
// are being saved.
func saveTrustedCert(cert *x509.Certificate) {
	if _, err := os.Stat(utils.TrustedCertsPath()); os.IsNotExist(err) {
		os.Create(utils.TrustedCertsPath())
	}
	pemCert := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: cert.Raw})
	trustedCerts, err := os.OpenFile(utils.TrustedCertsPath(), os.O_APPEND|os.O_WRONLY, 0600)
	utils.CheckBlockingError(err)
	trustedCerts.Write(pemCert)
}

// urlRemoveTrailingSlash takes url string as parameter
// and removes the trailing slash if there is any.
func urlRemoveTrailingSlash(url string) string {
	newUrl := []rune(url)
	if strings.HasSuffix(url, "/") {
		newUrl = newUrl[0 : len(newUrl)-1]
	}
	return string(newUrl)
}

// urlAppendDefaultPort takes url as parameter
// and appends the default https port(443).
func urlAppendDefaultPort(url string) string {
	url = urlRemoveTrailingSlash(url)
	if len(strings.Split(url, ":")) == 2 {
		return url
	}
	return url + HttpsDefaultPort
}

// getResponseError takes int as parameter which is
// response code and returns proper error.
// TODO: Expand with more code cases.
func getResponseError(code int) error {
	switch code {
	case 401:
		return Code401Error
	case 403:
		return Code403Error
	case 404:
		return Code404Error
	}
	return nil
}
