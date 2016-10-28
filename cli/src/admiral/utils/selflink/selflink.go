package selflink

import (
	"encoding/json"
	"fmt"
	"net/http"

	"admiral/client"
	"admiral/config"
	"admiral/utils"
)

var (
	NonUniqueIdMessage     = "Non-unique ID: %s provided for type: %s"
	NoElementsFoundMessage = "No elements found with ID: %s for type: %s"
)

type SelfLinkError struct {
	message string
	id      string
	resType utils.ResourceType
}

func (err *SelfLinkError) Error() string {
	return fmt.Sprintf(err.message, err.id, err.resType.GetName())
}

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
}

func GetFullId(shortId string, resList ResourceList, resType utils.ResourceType) (string, error) {
	url := config.URL + utils.GetIdFilterUrl(shortId, resType)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return "", respErr
	}
	err := json.Unmarshal(respBody, resList)
	utils.CheckJson(err)
	if resList.GetCount() > 1 {
		return "", NewSelfLinkError(NonUniqueIdMessage, shortId, resType)
	}
	if resList.GetCount() < 1 {
		return "", NewSelfLinkError(NoElementsFoundMessage, shortId, resType)
	}
	resource := resList.GetResource(0)
	return resource.GetID(), nil
}

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
