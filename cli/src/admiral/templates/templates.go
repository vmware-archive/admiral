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
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/functions"
)

type LightContainer struct {
	Name  string `json:"name"`
	Image string `json:"image"`
}

func (lc *LightContainer) FetchAndPrintCont(link string) {

	req, _ := http.NewRequest("GET", link, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		fmt.Println(respErr)
		return
	}
	err := json.Unmarshal(respBody, lc)
	if err != nil {
		fmt.Println(err.Error())
	} else {
		fmt.Printf("   Container Name: %-22s\tContainer Image: %s\n", lc.Name, lc.Image)
	}
}

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
	url := config.URL + "/templates?documentType=true&templatesOnly=true&q="
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
func (lt *TemplatesList) PrintWithoutContainers() {
	count := 1
	fmt.Printf("%-40s %-35s %-15s\n", "ID", "NAME", "CONTAINERS")
	for _, template := range lt.Results {
		if template.ParentDescriptionLink != "" {
			continue
		}
		contCnt := "n/a"
		if len(template.DescriptionLinks) > 0 {
			contCnt = fmt.Sprintf("%d", len(template.DescriptionLinks))
		}
		fmt.Printf("%-40s %-35s %-15s\n", template.GetID(), template.Name, contCnt)
		count++
	}
}

//PrintWithContainer prints already fetched template with containers inside them.
func (lt *TemplatesList) PrintWithContainer() {
	//TODO: Figure out some better for formatting for better vision on console.
	url := config.URL
	fmt.Printf("%-40s %-35s %-15s\n", "ID", "NAME", "CONTAINERS")
	for _, template := range lt.Results {
		if template.ParentDescriptionLink != "" {
			continue
		}
		contCnt := "n/a"
		if len(template.DescriptionLinks) > 0 {
			contCnt = fmt.Sprintf("%d", len(template.DescriptionLinks))
		}
		fmt.Printf("%-40s %-35s %-15s\n", template.GetID(), template.Name, contCnt)
		for _, link := range template.DescriptionLinks {
			currentUrl := url + link
			container := &LightContainer{}
			container.FetchAndPrintCont(currentUrl)
		}
	}
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
		return "", errors.New("Templates with duplicate name found, use ID to remove the desired one.")
	} else if len(tl.Results) < 1 {
		return "", errors.New("Template not found.")
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
