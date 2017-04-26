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

package tags

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/common/base_types"
	"admiral/common/utils"
	"admiral/config"
)

type TagError struct {
	invalidInput string
}

func (te *TagError) Error() string {
	msg := "Invalid tag format for input: %s. \"Use key:value\" format."
	return fmt.Sprintf(msg, te.invalidInput)
}

func NewTagError(input string) *TagError {
	return &TagError{
		invalidInput: input,
	}
}

type Tag struct {
	base_types.ServiceDocument

	Key   string `json:"key"`
	Value string `json:"value"`
}

func (t *Tag) String() string {
	return fmt.Sprintf("[%s:%s]", t.Key, t.Value)
}

func (t *Tag) GetID() string {
	return utils.GetResourceID(t.DocumentSelfLink)
}

func (t *Tag) SetKeyValues(input string) error {
	var key, val string

	if !strings.Contains(input, ":") {
		key = input
		val = ""
	} else {
		keyValArr := strings.Split(input, ":")
		if len(keyValArr) != 2 {
			return NewTagError(input)
		}
		key = strings.TrimSpace(keyValArr[0])
		val = strings.TrimSpace(keyValArr[1])
		if key == "" {
			return NewTagError(input)
		}
	}
	t.Key = key
	t.Value = val
	return nil
}

func NewTag(input string) (*Tag, error) {
	tag := &Tag{}
	err := tag.SetKeyValues(input)
	return tag, err
}

type TagList struct {
	DocumentLinks []string       `json:"documentLinks"`
	Documents     map[string]Tag `json:"documents"`
}

func (tl *TagList) GetCount() int {
	return len(tl.Documents)
}

type TagAssignmentRequest struct {
	ResourceLink   string `json:"resourceLink,omitempty"`
	TagsToAssign   []Tag  `json:"tagsToAssign,omitempty"`
	TagsToUnassign []Tag  `json:"tagsToUnassign,omitempty"`
	// Populated by this service with the entire set of tag links on the resource after the
	// assignment/unassignment have been completed.
	TagLinks []string `json:"tagLinks,omitempty"`
}

func GetTagIdByEqualKeyVals(input string, createIfNotExist bool) (string, error) {
	tagToMatch, err := NewTag(input)
	if err != nil {
		return "", err
	}

	filterUrl := config.URL + "/resources/tags?documentType=true&expand=true&$filter=key+eq+'%s'+and+value+eq+'%s'"
	filterUrl = fmt.Sprintf(filterUrl, strings.ToLower(tagToMatch.Key), strings.ToLower(tagToMatch.Value))

	req, _ := http.NewRequest("GET", filterUrl, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}

	tagList := &TagList{}
	err = json.Unmarshal(respBody, tagList)
	if err != nil {
		return "", err
	}

	if tagList.GetCount() < 1 {
		if createIfNotExist {
			return AddTag(tagToMatch)
		}
		return "", nil
	}

	tag := tagList.Documents[tagList.DocumentLinks[0]]
	return tag.GetID(), nil
}

func AddTag(tag *Tag) (string, error) {
	url := config.URL + "/resources/tags/"
	jsonBody, err := json.Marshal(tag)
	utils.CheckBlockingError(err)
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	_, respBody, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return "", respErr
	}
	newTag := &Tag{}
	err = json.Unmarshal(respBody, newTag)
	utils.CheckBlockingError(err)
	return newTag.GetID(), nil
}

func TagsToString(tagLinks []string) string {
	if len(tagLinks) == 0 {
		return "n/a"
	}
	var buffer bytes.Buffer
	for _, tl := range tagLinks {
		tag := getTag(tl)
		buffer.WriteString(tag.String())
	}
	return buffer.String()
}

func getTag(link string) *Tag {
	url := config.URL + link
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, _ := client.ProcessRequest(req)
	tag := &Tag{}
	json.Unmarshal(respBody, tag)
	return tag
}
