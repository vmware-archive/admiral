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

package selflink

import (
	"encoding/json"
	"fmt"
	"net/http"

	"admiral/client"
	"admiral/config"
	"admiral/utils"
	"errors"
)

var (
	NonUniqueIdMessage     = "%s: Non-unique ID and name: %s provided"
	NoElementsFoundMessage = "%s: No elements found with ID or name: %s"

	NonUniqueIdAndNoElementsWithName = "%s: Non-unique ID and no elements found for name: %s"
	NotFoundIdAndDuplicateName       = "%s: No elements found with ID and non-unique names for input: %s "

	NotFound  = errors.New("Not found")
	NonUnique = errors.New("Non-unique")
)

type SelfLinkError struct {
	message string
	id      string
	resType utils.ResourceType
}

func (err *SelfLinkError) Error() string {
	return fmt.Sprintf(err.message, err.resType.GetName(), err.id)
}

// Function factory to produce SelfLinkError.
func NewSelfLinkError(msg, id string, resType utils.ResourceType) *SelfLinkError {
	err := &SelfLinkError{
		message: msg,
		resType: resType,
		id:      id,
	}
	return err
}

type Identifiable interface {
	GetID() string
}

type ResourceList interface {
	GetCount() int
	GetResource(index int) Identifiable
	Renew()
}

// GetFullId is returns string and error. If error is != nil, the string is empty.
// otherwise the string contains the full ID. In order to invoke it properly, the first
// parameter is the short ID, the second parameter should be empty object which implements the interface
// ResourceList and the third parameter is constant of type utils.ResourceType to specify the type of the
// resource which you're trying to get the full ID.
func GetFullId(idOrName string, resList ResourceList, resType utils.ResourceType) (string, error) {
	var (
		id      string
		idErr   error
		nameErr error
	)
	id, idErr = getFullIdByShortId(idOrName, resList, resType)
	if idErr == nil {
		return id, nil
	}
	resList.Renew()
	id, nameErr = getFullIdByName(idOrName, resList, resType)
	if nameErr == nil {
		return id, nil
	}
	resultError := buildIdError(idErr, nameErr, idOrName, resType)
	return "", resultError
}

// GetFullIds is same as GetFullId but it's working with slice of short IDs and returns slice of full IDs.
func GetFullIds(shortIds []string, resList ResourceList, resType utils.ResourceType) ([]string, error) {
	fullIds := make([]string, 0)
	for _, shortId := range shortIds {
		fullId, err := GetFullId(shortId, resList, resType)
		if err != nil {
			return nil, err
		}
		fullIds = append(fullIds, fullId)
	}
	return fullIds, nil
}

func getFullIdByShortId(shortId string, resList ResourceList, resType utils.ResourceType) (string, error) {
	url := config.URL + utils.GetIdFilterUrl(shortId, resType)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	err := json.Unmarshal(respBody, resList)
	utils.CheckBlockingError(err)
	if resList.GetCount() > 1 {
		return "", NonUnique
	}
	if resList.GetCount() < 1 {
		return "", NotFound
	}
	resource := resList.GetResource(0)
	return resource.GetID(), nil
}

func getFullIdByName(name string, resList ResourceList, resType utils.ResourceType) (string, error) {
	url := config.URL + utils.GetNameFilterUrl(name, resType)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	err := json.Unmarshal(respBody, resList)
	utils.CheckBlockingError(err)
	if resList.GetCount() > 1 {
		return "", NonUnique
	}
	if resList.GetCount() < 1 {
		return "", NotFound
	}
	resource := resList.GetResource(0)
	return resource.GetID(), nil
}

func buildIdError(idError, nameError error, idOrName string, resType utils.ResourceType) error {
	if idError == NotFound && nameError == NotFound {
		return NewSelfLinkError(NoElementsFoundMessage, idOrName, resType)
	} else if idError == NonUnique && nameError == NonUnique {
		return NewSelfLinkError(NonUniqueIdMessage, idOrName, resType)
	} else if idError == NonUnique && nameError == NotFound {
		return NewSelfLinkError(NonUniqueIdAndNoElementsWithName, idOrName, resType)
	} else if idError == NotFound && nameError == NonUnique {
		return NewSelfLinkError(NotFoundIdAndDuplicateName, idOrName, resType)
	} else if _, ok := idError.(client.AuthorizationError); ok {
		return idError
	}
	return nil
}
