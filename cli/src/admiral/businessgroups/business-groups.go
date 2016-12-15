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

// GetID returns the ID by getting the last part
// of the Id if split by slash.
func (bg *BusinessGroup) GetID() string {
	return utils.GetResourceID(bg.Id)
}

type BusinessGroupList []BusinessGroup

// GetCount returns the count of fetched business groups.
func (bgl *BusinessGroupList) GetCount() int {
	return len(*bgl)
}

// GetResource returns resource at the specified index,
// which resource implements the interface selflink.Identifiable.
func (bgl *BusinessGroupList) GetResource(index int) selflink.Identifiable {
	resource := (*bgl)[index]
	return &resource
}

// FetchApps makes REST call to populate BusinessGroupList object
// with Business Groups. The url of this call is /groups/
func (bgl *BusinessGroupList) FetchBusinessGroups() (int, error) {
	url := config.URL + "/groups?documentType=true&expand=true"
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, bgl)
	utils.CheckBlockingError(err)
	return len(*bgl), nil
}

// GetOutputString returns raw string with information
// about business groups. It is used from "business-groups list" command, and
// this string requires formatting before printing it to the console.
func (bgl *BusinessGroupList) GetOutputString() string {
	if len(*bgl) < 1 {
		return "No elemnts found."
	}
	var buffer bytes.Buffer
	buffer.WriteString("ID\tLABEL\n")
	for _, bg := range *bgl {
		output := utils.GetTabSeparatedString(bg.GetID(), bg.Label)
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

// GetFullId is custom implementation to get the full id from
// short id for business groups. Requires custom implementation
// because business groups object are different from the standard
// Admiral objects.
func GetFullId(idOrLabel string) (string, error) {
	var (
		id      string
		idErr   error
		nameErr error
	)
	id, idErr = getFullIdByShortId(idOrLabel)
	if idErr == nil {
		return id, nil
	}
	id, nameErr = getFullIdByLabel(idOrLabel)
	if nameErr == nil {
		return id, nil
	}
	resultError := buildIdError(idErr, nameErr, idOrLabel, utils.BUSINESS_GROUP)
	return "", resultError
}

func getFullIdByShortId(shortId string) (string, error) {
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
		return "", selflink.NotFound
	}
	if matchedCount > 1 {
		return "", selflink.NonUnique
	}
	return bgl.GetResource(lastMatchIndex).GetID(), nil
}

func getFullIdByLabel(label string) (string, error) {
	bgl := &BusinessGroupList{}
	bgl.FetchBusinessGroups()
	matchedCount := 0
	lastMatchIndex := 0
	for i, bg := range *bgl {
		if label != bg.Label {
			continue
		}
		matchedCount++
		lastMatchIndex = i
	}
	if matchedCount < 1 {
		return "", selflink.NotFound
	}
	if matchedCount > 1 {
		return "", selflink.NonUnique
	}
	return bgl.GetResource(lastMatchIndex).GetID(), nil
}

func buildIdError(idError, nameError error, idOrName string, resType utils.ResourceType) error {
	if idError == selflink.NotFound && nameError == selflink.NotFound {
		return selflink.NewSelfLinkError(selflink.NoElementsFoundMessage, idOrName, resType)
	} else if idError == selflink.NonUnique && nameError == selflink.NonUnique {
		return selflink.NewSelfLinkError(selflink.NonUniqueIdMessage, idOrName, resType)
	} else if idError == selflink.NonUnique && nameError == selflink.NotFound {
		return selflink.NewSelfLinkError(selflink.NonUniqueIdAndNoElementsWithName, idOrName, resType)
	} else if idError == selflink.NotFound && nameError == selflink.NonUnique {
		return selflink.NewSelfLinkError(selflink.NotFoundIdAndDuplicateName, idOrName, resType)
	} else {
		return nil
	}
}
