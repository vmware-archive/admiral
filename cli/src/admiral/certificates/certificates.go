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

package certificates

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io/ioutil"
	"net/http"
	"strings"
	"time"

	"admiral/client"
	"admiral/config"
	"admiral/functions"
)

type Certificate struct {
	CommonName        string `json:"commonName"`
	IssuerName        string `json:"issuerName"`
	Serial            string `json:"serial"`
	Fingerprint       string `json:"fingerprint"`
	ValidSince        int64  `json:"validSince"`
	ValidTo           int64  `json:"validTo"`
	DocumentSelfLinks string `json:"documentSelfLink"`
}

//GetID returns string containing the last part of the document self link.
func (c *Certificate) GetID() string {
	return strings.Replace(c.DocumentSelfLinks, "/config/trust-certs/", "", -1)
}

//GetValidSince returns string showing since when is valid the certificate.
func (c *Certificate) GetValidSince() string {
	layout := "Jan 2, 2006"
	then := time.Unix(0, c.ValidSince*int64(time.Millisecond))
	return then.Format(layout)
}

//GetValidTo returns string showing to when is valid the certificate.
func (c *Certificate) GetValidTo() string {
	layout := "Jan 2, 2006"
	then := time.Unix(0, c.ValidTo*int64(time.Millisecond))
	return then.Format(layout)
}

//String returns information about certificate.
func (c *Certificate) String() string {
	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("Company name: %s\n", c.CommonName))
	buf.WriteString(fmt.Sprintf("Issuer name: %s\n", c.IssuerName))
	buf.WriteString(fmt.Sprintf("Serial: %s\n", c.Serial))
	buf.WriteString(fmt.Sprintf("SHA fingerprint: %s\n", c.Fingerprint))
	buf.WriteString(fmt.Sprintf("Valid since: %s\n", c.GetValidSince()))
	buf.WriteString(fmt.Sprintf("Valid to: %s", c.GetValidTo()))
	return buf.String()
}

type CertificateList struct {
	DocumentLinks []string               `json:"documentLinks"`
	Documents     map[string]Certificate `json:"documents"`
}

//FetchCertificates is fetching all certificates and returns their count.
func (cl *CertificateList) FetchCertificates() (int, error) {
	url := config.URL + "/config/trust-certs?expand"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, cl)
	functions.CheckJson(err)
	return len(cl.DocumentLinks), nil
}

//Print is printing already fetched certificates.
func (cl *CertificateList) Print() {
	if len(cl.DocumentLinks) < 1 {
		fmt.Println("n/a")
		return
	}
	fmt.Printf("%-25s %-15s %-15s %-20s\n", "ID", "NAME", "VALID SINCE", "VALID TO")
	for _, link := range cl.DocumentLinks {
		val := cl.Documents[link]
		fmt.Printf("%-25s %-15s %-15s %-20s\n", val.GetID(), val.IssuerName, val.GetValidSince(), val.GetValidTo())
	}
}

//GetCertLinks compares certificates' issuer names if match with the one
//provided as parameter and returns array with links of certificates which names matched.
func GetCertLinks(name string) []string {
	cl := CertificateList{}
	cl.FetchCertificates()
	links := make([]string, 0)
	for key, val := range cl.Documents {
		if name == val.IssuerName {
			links = append(links, key)
		}
	}
	return links
}

//RemoveCertificate looks for certificate with name matching the parameter.
//If find a single certificate with the same name, extract it's ID and pass it
//to the function that removes by id. Returns ID of the removed certificate and error,
//error is != nil if the count of certificates matching the name from parameter is less than 0,
//or more than 1.
func RemoveCertificate(name string) (string, error) {
	links := GetCertLinks(name)
	if len(links) < 1 {
		return "", errors.New("Certificate with that issuer name, not found.")
	}
	if len(links) > 1 {
		return "", errors.New("Certificates with duplicate names found. Please provide the ID for the specific certificate.")
	}
	id := functions.GetResourceID(links[0])
	return RemoveCertificateID(id)
}

//RemoveCertificateID removes certificate by the ID provided.
//Returns the ID of removed certificate and error, error is != nil
//if response code is != 200.
func RemoveCertificateID(id string) (string, error) {
	link := functions.CreateResLinkForCerts(id)
	url := config.URL + link
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil
}

//EditCertificate looks for certificate with name matching the parameter.
//If find a single certificate with the same name, extract it's ID and pass it
//to the function that edits by id. Returns ID of the edited certificate and error,
//error is != nil if the count of certificates matching the name from parameter is less than 0,
//or more than 1.
func EditCertificate(name, dirF, urlF string) (string, error) {
	links := GetCertLinks(name)
	if len(links) < 1 {
		return "", errors.New("Certificate with that issuer name, not found.")
	}
	if len(links) > 1 {
		return "", errors.New("Certificates with duplicate names found. Please provide the ID for the specific certificate.")
	}

	id := functions.GetResourceID(links[0])

	return EditCertificateID(id, dirF, urlF)
}

//EditCertificateID edits certificate by the ID provided.
//Returns the ID of removed certificate and error, error is != nil
//if response code is != 200.
func EditCertificateID(id, dirF, urlF string) (string, error) {
	if dirF != "" {
		importFile, err := ioutil.ReadFile(dirF)
		functions.CheckFile(err)
		cff := CertificateFromFile{
			Certificate: string(importFile),
		}
		jsonBody, err := json.Marshal(cff)
		url := config.URL + functions.CreateResLinkForCerts(id)
		req, _ := http.NewRequest("PATCH", url, bytes.NewBuffer(jsonBody))
		_, respBody, respErr := client.ProcessRequest(req)
		if respErr != nil {
			return "", respErr
		}
		cert := &Certificate{}
		err = json.Unmarshal(respBody, cert)
		functions.CheckJson(err)
		return cert.GetID(), nil
	} else if urlF != "" {
		//TODO
		return "", errors.New("Not implemented.")
	}
	return "", errors.New("No file or url provided.")
}

type CertificateFromFile struct {
	Certificate string `json:"certificate"`
}

//AddFromFile adds certificate from file which path is passed as parameter.
//Returns ID of the added certificate and error which is != nil if
//response code is != 200.
func AddFromFile(dirF string) (string, error) {
	importFile, err := ioutil.ReadFile(dirF)
	functions.CheckFile(err)
	cff := CertificateFromFile{
		Certificate: string(importFile),
	}
	jsonBody, err := json.Marshal(cff)
	url := config.URL + "/config/trust-certs"
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	cert := &Certificate{}
	err = json.Unmarshal(respBody, cert)
	functions.CheckJson(err)
	return cert.GetID(), nil
}

type CertificateFromUrl struct {
	HostUri string `json:"hostUri"`
}

//AddFromFile adds certificate from url that is passed as parameter.
//Returns ID of the added certificate and error which is != nil if
//response code is != 200.
func AddFromUrl(urlF string) (string, error) {
	cfu := CertificateFromUrl{
		HostUri: urlF,
	}
	jsonBody, err := json.Marshal(cfu)
	functions.CheckJson(err)
	url := config.URL + "/config/trust-certs-import"
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	cert := &Certificate{}
	err = json.Unmarshal(respBody, cert)
	functions.CheckJson(err)
	return cert.GetID(), nil
}

//CheckTrustCert is printing the prompted new certificate,
//waiting for user to agree or disagree. Returns false if user
//disagree or if response code is != 200 after user's agreement.
//Returns true if user agree and response code is equal to 200.
func CheckTrustCert(respBody []byte, autoAccept bool) bool {
	cert := &Certificate{}
	err := json.Unmarshal(respBody, cert)
	functions.CheckJson(err)
	url := config.URL + "/config/trust-certs"
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(respBody))
	req.Header.Add("Pragma", "xn-force-index-update")
	if autoAccept {
		client.ProcessRequest(req)
		return true
	}
	fmt.Println(cert)
	fmt.Println("Are you sure you want to connect to this site? (y/n)?")
	answer := functions.PromptAgreement()

	if answer == "No" || answer == "no" {
		return false
	}

	_, _, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return false
	}
	return true
}
