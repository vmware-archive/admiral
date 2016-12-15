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
		body, err := ioutil.ReadAll(resp.Body)
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

// checkForCertErrors checks if response error is related
// to x509 certificate errors. In case the error is about,
// selfsigned certificate, it prompts the user to accept it.
// In case it's other x509 related error it loads already accepted
// certificates and if the problematic one is included there it
// turns off SSL verification of the http client, otherwise, prompts
// the user to accept the certificate. In both cases if the user
// decline the prompted certificates, the program execution is aborted.
func checkForCertErrors(url string, errA error) (bool, error) {
	if errA == nil {
		return false, errA
	}

	//if strings.Contains(errA.Error(), "x509: certificate signed by unknown authority") {
	//	result := promptAllCerts(url)
	//	if !result {
	//		utils.CheckBlockingError(errors.New("Certificate declined, command execution aborted."))
	//	}
	//	return true, nil
	//}

	if strings.Contains(errA.Error(), "x509") {
		loadCertsFromFile()
		skipVerify := checkCertInLoadedCerts(url)
		if skipVerify {
			skipSSLVerification = skipVerify
			return true, nil
		}
	}

	return false, errA
}

// checkCertInLoadedCerts first loads already trusted certificates from the user.
// then it makes tls dial to the url to fetch the certificates and checks if
// they are contained in the slice of already loaded. If they are not, it prompts the
// user to accept them.
func checkCertInLoadedCerts(url string) bool {
	url = urlAppendDefaultPort(url)
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
		answer := promptCertAgreement(cert)
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

// promptAllCerts makes tls dial to fetch server certificates,
// and then prompts all of them to the user.
func promptAllCerts(url string) bool {
	url = urlAppendDefaultPort(url)
	conn, err := tls.Dial("tcp", url, &tls.Config{InsecureSkipVerify: true})
	if err != nil {
		return false
	}
	cs := conn.ConnectionState()
	fmt.Println(cs.HandshakeComplete)
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

// promptCertAgreement takes x509 certificate as parameter and prompts the user
// If the user accept it, the function returns true, otherwise returns false.
func promptCertAgreement(cert *x509.Certificate) bool {
	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("Common Name: %s\n", cert.Issuer.CommonName))
	buf.WriteString(fmt.Sprintf("Serial: %s\n", cert.SerialNumber))
	buf.WriteString(fmt.Sprintf("Valid since: %s\n", cert.NotBefore))
	buf.WriteString(fmt.Sprintf("Valid to: %s", cert.NotAfter))
	fmt.Println(buf.String())
	fmt.Println("Are you sure you want to connect to this site? (y/n)?")
	answer := utils.PromptAgreement()

	if !answer {
		return false
	}
	return true
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

// loadCertsFromFile loads the user trusted certificates, into
// slice of x509 certificates.
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

// containsCert takes x509 certificate as parameter
// and checks if this certificate is included into
// the slice of already loaded user trusted certificates.
func containsCert(cert *x509.Certificate) bool {
	for i := range loadedTrustCerts {
		if cert.Equal(loadedTrustCerts[i]) {
			return true
		}
	}
	return false
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
