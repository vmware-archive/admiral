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

package businessgroups

import (
	"bytes"
	"encoding/json"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/utils"
	"admiral/utils/selflink"
)

type BusinessGroup struct {
	Id    string `json:"id"`
	Label string `json:"label"`
}

func (bg *BusinessGroup) GetID() string {
	return utils.GetResourceID(bg.Id)
}

type BusinessGroupList []BusinessGroup

func (bgl *BusinessGroupList) GetCount() int {
	return len(*bgl)
}

func (bgl *BusinessGroupList) GetResource(index int) selflink.Identifiable {
	resource := (*bgl)[index]
	return &resource
}
func (bgl *BusinessGroupList) FetchBusinessGroups() (int, error) {
	url := config.URL + "/groups?documentType=true&expand=true"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, bgl)
	utils.CheckJson(err)
	return len(*bgl), nil
}

func (bgl *BusinessGroupList) GetOutputString() string {
	if len(*bgl) < 1 {
		return "No elemnts found."
	}
	var buffer bytes.Buffer
	buffer.WriteString("ID\tLABEL\n")
	for _, bg := range *bgl {
		output := utils.GetFormattedString(bg.GetID(), bg.Label)
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

func GetFullId(shortId string) (string, error) {
	bgl := &BusinessGroupList{}
	bgl.FetchBusinessGroups()
	matchedCount := 0
	lastMatchIndex := 0
	for i, bg := range *bgl {
		if !strings.HasPrefix(bg.GetID(), shortId) {
			continue
		}
		matchedCount++
		lastMatchIndex = i
	}
	if matchedCount < 1 {
		return "", selflink.NewSelfLinkError(selflink.NoElementsFoundMessage, shortId, utils.BUSINESS_GROUP)
	}
	if matchedCount > 1 {
		return "", selflink.NewSelfLinkError(selflink.NonUniqueIdMessage, shortId, utils.BUSINESS_GROUP)
	}
	return bgl.GetResource(lastMatchIndex).GetID(), nil
}
