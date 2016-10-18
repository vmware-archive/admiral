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

package templates

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/functions"
	"sort"
)

var (
	DuplicateNamesError   = errors.New("Templates with duplicate name found, use ID to remove the desired one.")
	TemplateNotFoundError = errors.New("Template not found.")
)

type LightContainer struct {
	Name  string `json:"name"`
	Image string `json:"image"`
}

func (lc *LightContainer) GetOutput(link string) (string, error) {
	req, _ := http.NewRequest("GET", link, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	err := json.Unmarshal(respBody, lc)
	functions.CheckJson(err)
	return fmt.Sprintf("   Container Name: %-22s\tContainer Image: %s\n", lc.Name, lc.Image), nil
}

type TemplateSorter []Template

func (ts TemplateSorter) Len() int           { return len(ts) }
func (ts TemplateSorter) Swap(i, j int)      { ts[i], ts[j] = ts[j], ts[i] }
func (ts TemplateSorter) Less(i, j int) bool { return ts[i].Name < ts[j].Name }

type Template struct {
	Name                  string   `json:"name"`
	TemplateType          string   `json:"templateType"`
	DescriptionLinks      []string `json:"descriptionLinks"`
	DocumentSelfLink      *string  `json:"documentSelfLink"`
	IsAutomated           bool     `json:"is_automated"`
	IsOfficial            bool     `json:"is_official"`
	StarsCount            int32    `json:"star_count"`
	Description           string   `json:"description"`
	ParentDescriptionLink string   `json:"parentDescriptionLink"`
}

//GetID returns the ID of the template.
func (t *Template) GetID() string {
	return strings.Replace(*t.DocumentSelfLink, "/resources/composite-descriptions/", "", -1)
}

type TemplatesList struct {
	Results []Template `json:"results"`
}

//FetchTemplates fetches the templates by query. If it's needed to
//fetch all templates, empty string should be passed. Returns the
//count of fetched templates.
func (lt *TemplatesList) FetchTemplates(queryF string) (int, error) {
	url := config.URL + "/templates?documentType=true&templatesOnly=true"
	var query string
	if queryF != "" {
		query = fmt.Sprintf("&q=%s", queryF)
		url = url + query
	} else {
		query = "&q=*"
		url = url + query
	}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, lt)
	functions.CheckJson(err)
	return len(lt.Results), nil
}

//PrintWithoutContainers prints already fetched templates without
//printing containers inside the templates.
func (lt *TemplatesList) GetOutputStringWithoutContainers() string {
	var buffer bytes.Buffer
	if len(lt.Results) < 1 {
		return "No elements found."
	}
	sort.Sort(TemplateSorter(lt.Results))
	buffer.WriteString("ID\tNAME\tCONTAINERS\n")
	for _, template := range lt.Results {
		if template.ParentDescriptionLink != "" {
			continue
		}
		contCnt := "n/a"
		if len(template.DescriptionLinks) > 0 {
			contCnt = fmt.Sprintf("%d", len(template.DescriptionLinks))
		}
		output := functions.GetFormattedString(template.GetID(), template.Name, contCnt)
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

//PrintWithContainer prints already fetched template with containers inside them.
func (lt *TemplatesList) GetOutputStringWithContainers() (string, error) {
	//TODO: Figure out some better for formatting for better vision on console.
	url := config.URL
	var buffer bytes.Buffer
	if len(lt.Results) < 1 {
		return "No elements found.", nil
	}
	buffer.WriteString("ID\tNAME\tCONTAINERS\n")
	for _, template := range lt.Results {
		if template.ParentDescriptionLink != "" {
			continue
		}
		contCnt := "n/a"
		if len(template.DescriptionLinks) > 0 {
			contCnt = fmt.Sprintf("%d", len(template.DescriptionLinks))
		}
		output := functions.GetFormattedString(template.GetID(), template.Name, contCnt)
		buffer.WriteString(output)
		buffer.WriteString("\n")
		for _, link := range template.DescriptionLinks {
			currentUrl := url + link
			container := &LightContainer{}
			output, err := container.GetOutput(currentUrl)
			if err != nil {
				return "", err
			}
			buffer.WriteString(output)
			buffer.WriteString("\n")
		}
	}
	return strings.TrimSpace(buffer.String()), nil
}

//GetTemplateLinks returns array of self links of templates which names
//match the one passed as parameter.
func (lt *TemplatesList) GetTemplateLinks(tmplName string) []string {
	links := make([]string, 0)
	for _, tmpl := range lt.Results {
		if tmplName == tmpl.Name {
			var selfLink string
			if tmpl.TemplateType != "CONTAINER_IMAGE_DESCRIPTION" {
				selfLink = *tmpl.DocumentSelfLink
				links = append(links, selfLink)
			}
		}
	}
	return links
}

//RemoveTemplate removes template by name passed as parameter.
//Returns the ID of the removed template and error = nil or
//ID = empty string and error != nil.
func RemoveTemplate(name string) (string, error) {
	tl := &TemplatesList{}
	tl.FetchTemplates(name)
	if len(tl.Results) > 1 {
		return "", DuplicateNamesError
	} else if len(tl.Results) < 1 {
		return "", TemplateNotFoundError
	}

	id := functions.GetResourceID(*tl.Results[0].DocumentSelfLink)
	return RemoveTemplateID(id)
}

//RemoveTemplateID removes template by ID passed as parameter.
//Returns the ID of the removed template and error = nil or
//ID = empty string and error != nil.
func RemoveTemplateID(id string) (string, error) {
	url := config.URL + "/resources/composite-descriptions/" + id
	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Add("Pragma", "xn-force-index-update")
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	template := &Template{}
	err := json.Unmarshal(respBody, template)
	functions.CheckJson(err)
	for i := range template.DescriptionLinks {
		tempLink := config.URL + template.DescriptionLinks[i]
		req, _ := http.NewRequest("DELETE", tempLink, nil)
		client.ProcessRequest(req)
	}
	req, _ = http.NewRequest("DELETE", url, nil)
	_, _, respErr = client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	return id, nil

}

func Import(dirF string) (string, error) {
	importFile, err := ioutil.ReadFile(dirF)

	if err != nil {
		return "", err
	}

	url := config.URL + "/resources/composite-templates"

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(importFile))
	req.Header.Set("Content-Type", "application/yaml")
	resp, _, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return "", respErr
	}

	link := resp.Header.Get("Location")
	id := functions.GetResourceID(link)
	return id, nil
}

func Export(id, dirF, format string) (string, error) {
	file, err := verifyFile(dirF)
	if err != nil {
		return "", err
	}
	url := config.URL + "/resources/composite-templates?selfLink=" + id
	if format == "docker" {
		url = url + "&format=docker"
	}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		os.Remove(dirF)
		return "", err
	}
	_, err = file.Write(respBody)

	if err != nil {
		os.Remove(dirF)
		return "", err
	}
	return id, nil
}

//Function to verify if file can be created.
//Returns the file and result of verification
func verifyFile(dirF string) (*os.File, error) {
	file, err := os.Create(dirF)
	return file, err
}
