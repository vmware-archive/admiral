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
	"bytes"
	"encoding/json"
	"net/http"
	"sort"
	"strings"

	"admiral/client"
	"admiral/common/base_types"
	"admiral/common/utils"
	"admiral/common/utils/selflink_utils"
	"admiral/common/utils/uri_utils"
)

type ImageSorter []Image

func (is ImageSorter) Len() int      { return len(is) }
func (is ImageSorter) Swap(i, j int) { is[i], is[j] = is[j], is[i] }
func (is ImageSorter) Less(i, j int) bool {
	if is[i].StarsCount == is[j].StarsCount {
		return is[i].Name < is[j].Name
	}
	return is[i].StarsCount > is[j].StarsCount
}

type Image struct {
	base_types.ServiceDocument

	Name             string   `json:"name"`
	TemplateType     string   `json:"templateType"`
	DescriptionLinks []string `json:"descriptionLinks"`
	IsAutomated      bool     `json:"is_automated"`
	IsOfficial       bool     `json:"is_official"`
	IsTrusted        bool     `json:"is_trusted"`
	StarsCount       int32    `json:"star_count"`
	Description      string   `json:"description"`
}

func (i *Image) GetShortName() string {
	return cutImgName(i.Name)
}

type ImagesList struct {
	Results []Image `json:"results"`
}

func (li *ImagesList) GetOuputString() string {
	var buffer bytes.Buffer
	if len(li.Results) > 0 {
		sort.Sort(ImageSorter(li.Results))
		buffer.WriteString("NAME\tDESCRIPTION\tSTARS\tOFFICIAL\tAUTOMATED\tTRUSTED\n")
		for _, image := range li.Results {
			var (
				desc      string
				official  string
				automated string
				trusted   string
			)

			desc = utils.ShortString(image.Description, 40)

			if image.IsAutomated {
				automated = "[OK]"
			}

			if image.IsOfficial {
				official = "[OK]"
			}

			if image.IsTrusted {
				trusted = "[OK]"
			}
			output := utils.GetTabSeparatedString(image.GetShortName(), desc, image.StarsCount, official, automated, trusted)
			buffer.WriteString(output)
			buffer.WriteString("\n")
		}
	} else {
		return selflink_utils.NoElementsFoundMessage
	}
	return strings.TrimSpace(buffer.String())
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
func (li *ImagesList) QueryImages(imgName string) (int, error) {
	cqm := uri_utils.GetCommonQueryMap()
	cqm["q"] = imgName
	url := uri_utils.BuildUrl(uri_utils.Image, cqm, true)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, li)
	utils.CheckBlockingError(err)
	return len(li.Results), nil
}

type PopularImages []Image

//PrintPopular prints popular images.
//This function is called when user execute "admiral search"
//without passing any name as parameter.
func GetPopular() (string, error) {
	url := uri_utils.BuildUrl(uri_utils.PopularImages, uri_utils.GetCommonQueryMap(), true)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	pi := PopularImages{}
	err := json.Unmarshal(respBody, &pi)
	utils.CheckBlockingError(err)
	var buffer bytes.Buffer
	buffer.WriteString("POPULAR TEMPLATES\n")
	buffer.WriteString("NAME\tDESCRIPTION\tSTARS\tOFFICIAL\tAUTOMATED\tTRUSTED\n")
	for _, img := range pi {
		cuttedName := cutImgName(img.Name)
		desc := utils.ShortString(img.Description, 40)
		output := utils.GetTabSeparatedString(cuttedName, desc, "---", "---", "---", "---")
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String()), nil
}
