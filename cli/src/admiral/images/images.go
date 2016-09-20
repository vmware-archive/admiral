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

package images

import (
	"encoding/json"
	"fmt"
	"net/http"
	"sort"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/functions"
)

type ImageSorter []Image

func (is ImageSorter) Len() int           { return len(is) }
func (is ImageSorter) Swap(i, j int)      { is[i], is[j] = is[j], is[i] }
func (is ImageSorter) Less(i, j int) bool { return is[i].StarsCount > is[j].StarsCount }

type Image struct {
	Name             string   `json:"name"`
	TemplateType     string   `json:"templateType"`
	DescriptionLinks []string `json:"descriptionLinks"`
	DocumentSelfLink string   `json:"documentSelfLink"`
	IsAutomated      bool     `json:"is_automated"`
	IsOfficial       bool     `json:"is_official"`
	IsTrusted        bool     `json:"is_trusted"`
	StarsCount       int32    `json:"star_count"`
	Description      string   `json:"description"`
}

type ImagesList struct {
	Results []Image `json:"results"`
}

func (li *ImagesList) Print() {
	if len(li.Results) > 0 {
		sort.Sort(ImageSorter(li.Results))
		fmt.Printf("%-55s %-45s %-10s %-10s %-10s %-10s\n", "NAME", "DESCRIPTION", "STARS", "OFFICIAL", "AUTOMATED", "TRUSTED")
		for _, image := range li.Results {
			var (
				desc      string
				official  string
				automated string
				trusted   string
			)

			desc = functions.ShortString(image.Description, 40)

			if image.IsAutomated {
				automated = "[OK]"
			}

			if image.IsOfficial {
				official = "[OK]"
			}

			if image.IsTrusted {
				trusted = "[OK]"
			}
			cuttedName := cutImgName(image.Name)
			fmt.Printf("%-55s %-45s %-10d %-10s %-10s %-10s\n", cuttedName, desc, image.StarsCount, official, automated, trusted)
		}
	} else {
		fmt.Println("No results.")
	}
}

//cutImgName removes any default path that name is containing.
func cutImgName(name string) string {
	officialRegAddresses := []string{
		"registry.hub.docker.com/library/",
		"registry.hub.docker.com/",
		"docker.io/library/",
		"docker.io/",
	}

	if name == "" {
		return ""
	}

	for _, regPath := range officialRegAddresses {
		if strings.HasPrefix(name, regPath) {
			return name[len(regPath):]
		}
	}
	return name
}

//QueryImages fetches images matching the imgName parameter.
//The function returns the count of the fetched images.
func (li *ImagesList) QueryImages(imgName string) int {
	url := config.URL + "/templates?&documentType=true&imagesOnly=true&q=" + imgName
	req, _ := http.NewRequest("GET", url, nil)
	resp, respBody := client.ProcessRequest(req)
	err := json.Unmarshal(respBody, li)
	functions.CheckJson(err)
	defer resp.Body.Close()
	return len(li.Results)
}

type PopularImages []Image

//PrintPopular prints popular images.
//This function is called when user execute "admiral search"
//without passing any name as parameter.
func PrintPopular() {
	url := config.URL + "/popular-images?documentType=true"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody := client.ProcessRequest(req)
	pi := PopularImages{}
	err := json.Unmarshal(respBody, &pi)
	functions.CheckJson(err)
	fmt.Println("POPULAR TEMPLATES")
	fmt.Printf("%-30s %-45s %-10s %-10s %-10s %-10s\n", "NAME", "DESCRIPTION", "STARS", "OFFICIAL", "AUTOMATED", "TRUSTED")
	for _, img := range pi {
		cuttedName := cutImgName(img.Name)
		desc := functions.ShortString(img.Description, 40)
		fmt.Printf("%-30s %-45s %-10s %-10s %-10s %-10s\n", cuttedName, desc, "---", "---", "---", "---")
	}
}
