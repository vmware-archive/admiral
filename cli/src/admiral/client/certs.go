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
	"encoding/pem"
	"errors"
	"fmt"
	"io/ioutil"
	"net/url"
	"os"

	"admiral/utils"
)

var (
	InvalidUrlScheme = errors.New("Invalid URL scheme. http/https supported only.")
)

type trustedCertificates struct {
	loadedTrustedCerts []*x509.Certificate
}

// loadCertsFromFile loads the user trusted certificates, into
// slice of x509 certificates.
func (tc *trustedCertificates) loadCertsFromFile() error {
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
	tc.loadedTrustedCerts = certs
	return nil
}

// containsCert takes x509 certificate as parameter
// and checks if this certificate is included into
// the slice of already loaded user trusted certificates.
func (tc *trustedCertificates) containsCert(cert *x509.Certificate) bool {
	for i := range tc.loadedTrustedCerts {
		if cert.Equal(tc.loadedTrustedCerts[i]) {
			return true
		}
	}
	return false
}

// promptCertAgreement takes x509 certificate as parameter and prompts the user
// If the user accept it, the function returns true, otherwise returns false.
func promptCertAgreement(cert *x509.Certificate) bool {
	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("Common Name: %s\n", cert.Issuer.CommonName))
	buf.WriteString(fmt.Sprintf("Serial: %s\n", cert.SerialNumber))
	buf.WriteString(fmt.Sprintf("Valid since: %s\n", cert.NotBefore))
	buf.WriteString(fmt.Sprintf("Valid to: %s\n", cert.NotAfter))
	buf.WriteString(fmt.Sprint("Are you sure you want to connect to this site? (y/n)?"))
	fmt.Println(buf.String())
	answer := utils.PromptAgreement()

	if !answer {
		return false
	}
	return true
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

func validateConnection(url *url.URL) {
	if url.Scheme == "http" {
		return
	} else if url.Scheme == "https" && utils.InsecureConnection {
		skipSSLVerification = true
	} else if url.Scheme == "https" {
		skipSSLVerification = validateCertificates(url)
		if !skipSSLVerification {
			os.Exit(1)
		}
	} else {
		// Terminate if scheme is not http/s.
		utils.CheckBlockingError(InvalidUrlScheme)
	}
}

func validateCertificates(url *url.URL) bool {
	trustedCerts := &trustedCertificates{}
	trustedCerts.loadCertsFromFile()

	tlsUrl := urlAppendDefaultPort(url.Host)
	conn, err := tls.Dial("tcp", tlsUrl, &tls.Config{InsecureSkipVerify: true})
	utils.CheckBlockingError(err)

	cs := conn.ConnectionState()

	for i := 0; i < len(cs.PeerCertificates); i++ {
		if trustedCerts.containsCert(cs.PeerCertificates[i]) {
			return true
		}
	}

	for _, cert := range cs.PeerCertificates {
		answer := promptCertAgreement(cert)
		if answer {
			saveTrustedCert(cert)
			return true
		}
	}
	return false
}
