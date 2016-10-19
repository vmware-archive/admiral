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
	"encoding/pem"
)

var (
	Code401Error      = errors.New("HTTP Status 401 - Authentication required")
	NullResponseError = errors.New("Response from the server is null.")
)

type ResponseError struct {
	Message string `json:"message"`
}

var (
	skipSSLVerification bool
	loadedTrustCerts    []*x509.Certificate
)

//ProcessRequest is used for common requests. As parameter is taking
//request with only set method, url and body if there is one, then it handles
//things like adding auth token, sending the requests and checks if verbose boolean
//is true to print both requests and responses. Returns the response and the
//response body as byte array.
func ProcessRequest(req *http.Request) (*http.Response, []byte, error) {
	token, from := utils.GetAuthToken()
	netClient, err := buildHttpClient()
	if err != nil {
		return nil, nil, err
	}
	setReqHeaders(req, token)
	utils.CheckVerboseRequest(req)
	if err != nil {
		return nil, nil, err
	}
	resp, err := netClient.Do(req)
	redo, err := checkForCertErrors(req.URL.Host, err)
	if err != nil {
		return nil, nil, err
	}
	if redo {
		resp, respBody, err := ProcessRequest(req)
		return resp, respBody, err
	}
	admiralHostUrl := req.URL.Scheme + "://" + req.URL.Host
	utils.CheckResponse(err, admiralHostUrl)
	utils.CheckVerboseResponse(resp)

	if err = CheckResponseError(resp, from); err != nil {
		return resp, nil, err
	}

	respBody, err := ioutil.ReadAll(resp.Body)
	resp.Body.Close()
	return resp, respBody, nil
}

//CheckResponseError checks if the response code is 4xx and then prints any error message,
//or if the message is "forbidden", prints that there is authentication problem.
func CheckResponseError(resp *http.Response, tokenFrom string) error {
	if resp == nil {
		return NullResponseError
	}
	if resp.StatusCode >= 400 && resp.StatusCode <= 500 {
		if resp.StatusCode == 401 && resp.Body == nil {
			return Code401Error
		}
		body, err := ioutil.ReadAll(resp.Body)
		utils.CheckJson(err)
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
			return err
		}
		if message.Message == "forbidden" {
			return errors.New("Authorization error. Token used from " + tokenFrom)
		}
		if message.Message == "" {
			return errors.New("Connection error " + resp.Status)
		}
		return errors.New(message.Message)
	}
	return nil
}

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

	var timeoutDuration = time.Second * time.Duration(config.CLIENT_TIMEOUT)

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

//setupCaPool is taking the system CA's plus certificates
//from admiral directory which the user already accepted.
//It returns the created cert pool and error if occurred anywhere.
func setupCAPool() (*x509.CertPool, error) {
	caCertPool, _ := x509.SystemCertPool()
	if caCertPool == nil {
		caCertPool = x509.NewCertPool()
	}

	if _, err := os.Stat(utils.TrustedCertsPath()); os.IsNotExist(err) {
		os.Create(utils.TrustedCertsPath())
	}
	trustedCerts, err := ioutil.ReadFile(utils.TrustedCertsPath())

	if err != nil {
		return nil, err
	}
	caCertPool.AppendCertsFromPEM(trustedCerts)
	return caCertPool, nil
}

func checkForCertErrors(url string, errA error) (bool, error) {
	if errA == nil {
		return false, errA
	} else if strings.Contains(errA.Error(), "x509: certificate signed by unknown authority") {
		result := promptAllCerts(url)
		if result {
			return true, nil
		} else {
			os.Exit(0)
		}
	} else if strings.Contains(errA.Error(), "x509") {
		loadCertsFromFile()
		skipVerify := checkCertInLoadedCerts(url)
		if skipVerify {
			skipSSLVerification = skipVerify
			return true, nil
		} else {
			return false, nil
		}
	} else {
		return false, errA
	}
	return false, nil
}

func checkCertInLoadedCerts(url string) bool {
	conn, err := tls.Dial("tcp", url, &tls.Config{InsecureSkipVerify: true})
	if err != nil {
		return false
	}
	cs := conn.ConnectionState()
	result := false
	for _, cert := range cs.PeerCertificates {
		if containsCert(cert) {
			result = true
			continue
		}
		answer := prompCertAgreement(cert)
		if answer {
			cert.IsCA = true
			saveTrustedCert(cert)
			result = true
			continue
		} else {
			result = false
			break
		}
	}
	return result
}

func promptAllCerts(url string) bool {
	conn, err := tls.Dial("tcp", url, &tls.Config{InsecureSkipVerify: true})
	if err != nil {
		return false
	}
	cs := conn.ConnectionState()
	answer := false
	for _, cert := range cs.PeerCertificates {
		if prompCertAgreement(cert) {
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

func prompCertAgreement(cert *x509.Certificate) bool {
	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("Common Name: %s\n", cert.Issuer.CommonName))
	buf.WriteString(fmt.Sprintf("Serial: %s\n", cert.SerialNumber))
	buf.WriteString(fmt.Sprintf("Valid since: %s\n", cert.NotBefore))
	buf.WriteString(fmt.Sprintf("Valid to: %s", cert.NotAfter))
	fmt.Println(buf.String())
	fmt.Println("Are you sure you want to connect to this site? (y/n)?")
	answer := utils.PromptAgreement()

	if answer == "n" || answer == "N" {
		return false
	}
	return true
}

func saveTrustedCert(cert *x509.Certificate) {
	if _, err := os.Stat(utils.TrustedCertsPath()); os.IsNotExist(err) {
		os.Create(utils.TrustedCertsPath())
	}
	pemCert := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: cert.Raw})
	trustedCerts, err := os.OpenFile(utils.TrustedCertsPath(), os.O_APPEND|os.O_WRONLY, 0600)
	utils.CheckFile(err)
	trustedCerts.Write(pemCert)
}

func loadCertsFromFile() error {
	certBytes, err := ioutil.ReadFile(utils.TrustedCertsPath())
	if err != nil {
		return err
	}
	if certBytes == nil {
		return nil
	}
	blocks, _ := pem.Decode(certBytes)
	if blocks == nil {
		return nil
	}
	certs, err := x509.ParseCertificates(blocks.Bytes)
	if err != nil {
		return err
	}
	loadedTrustCerts = certs
	return nil
}

func containsCert(cert *x509.Certificate) bool {
	for i := range loadedTrustCerts {
		if cert.Equal(loadedTrustCerts[i]) {
			return true
		}
	}
	return false
}
