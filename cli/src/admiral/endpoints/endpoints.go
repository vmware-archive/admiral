package endpoints

import (
	"bytes"
	"encoding/json"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/utils"
	"admiral/utils/selflink"
	"errors"
)

const (
	AzureEndpoint   = "azure"
	AwsEndpoint     = "aws"
	VsphereEndpoint = "vsphere"

	AddEndpointUrl   = "/config/endpoints?enumerate"
	FetchEndpointUrl = "/config/endpoints?documentType=true&expand=true"
)

var (
	RequiredParametersMissingError = errors.New("Required parameters are missing.")
)

type EndpointProperties struct {
	PrivateKey   string `json:"privateKey,omitempty"`
	PrivateKeyId string `json:"privateKeyId,omitempty"`
	RegionId     string `json:"regionId,omitempty"`

	UserLink      string `json:"userLink,omitempty"`
	AzureTenantId string `json:"azureTenantId,omitempty"`
	HostName      string `json:"hostName,omitempty"`
}

type Endpoint struct {
	EndpointType       string              `json:"endpointType,omitempty"`
	EndpointProperties *EndpointProperties `json:"endpointProperties,omitempty"`
	Name               string              `json:"name,omitempty"`
	DocumentSelfLink   string              `json:"documentSelfLink,omitempty"`

	AuthCredentialsLink    string `json:"authCredentialsLink,omitempty"`
	ComputeLink            string `json:"computeLink,omitempty"`
	ComputeDescriptionLink string `json:"computeDescriptionLink,omitempty"`
	ResourcePoolLink       string `json:"resourcePoolLink,omitempty"`
	Id                     string `json:"id,omitempty"`

	DocumentVersion              int    `json:"documentVersion,omitempty"`
	DocumentEpoch                int64  `json:"documentEpoch,omitempty"`
	DocumentKind                 string `json:"documentKind,omitempty"`
	DocumentUpdateTimeMicros     int64  `json:"documentUpdateTimeMicros,omitempty"`
	DocumentUpdateAction         string `json:"documentUpdateAction,omitempty"`
	DocumentExpirationTimeMicros int64  `json:"documentExpirationTimeMicros,omitempty"`
	DocumentOwner                string `json:"documentOwner,omitempty"`
	DocumentAuthPrincipalLink    string `json:"documentAuthPrincipalLink,omitempty"`
}

func NewEndpoint(name, endpointType string) *Endpoint {
	return &Endpoint{
		Name:               name,
		EndpointType:       endpointType,
		EndpointProperties: new(EndpointProperties),
	}
}

func (e *Endpoint) GetID() string {
	return utils.GetResourceID(e.DocumentSelfLink)
}

type EndpointList struct {
	Documents     map[string]Endpoint `json:"documents"`
	DocumentLinks []string            `json:"documentLinks"`
}

func (el *EndpointList) GetCount() int {
	return len(el.DocumentLinks)
}

func (el *EndpointList) GetResource(index int) selflink.Identifiable {
	resource := el.Documents[el.DocumentLinks[index]]
	return &resource
}

func (el *EndpointList) FetchEndpoints() (int, error) {
	url := config.URL + FetchEndpointUrl
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)
	if respErr != nil {
		return 0, respErr
	}
	err := json.Unmarshal(respBody, el)
	utils.CheckBlockingError(err)
	return len(el.DocumentLinks), nil
}

func (el *EndpointList) GetOutputString() string {
	if el.GetCount() < 1 {
		return utils.NoElementsFoundMessage
	}
	var buffer bytes.Buffer
	buffer.WriteString("ID\tNAME\tTYPE\n")
	for _, link := range el.DocumentLinks {
		val := el.Documents[link]
		output := utils.GetTabSeparatedString(val.GetID(), val.Name, val.EndpointType)
		buffer.WriteString(output)
		buffer.WriteString("\n")
	}
	return strings.TrimSpace(buffer.String())
}

func AddAwsEndpoint(name, accessKey, secretKey, regionId string) (string, error) {
	if accessKey == "" || secretKey == "" || regionId == "" {
		return "", RequiredParametersMissingError
	}
	awsEndpoint := NewEndpoint(name, AwsEndpoint)
	awsEndpoint.EndpointProperties.PrivateKey = secretKey
	awsEndpoint.EndpointProperties.PrivateKeyId = accessKey
	awsEndpoint.EndpointProperties.RegionId = regionId

	return processEndpointAddRequest(awsEndpoint)
}

func AddAzureEndpoint(name, accessKey, secretKey, regionId, subscriptionId, tenantId string) (string, error) {
	if accessKey == "" || secretKey == "" || regionId == "" || subscriptionId == "" || tenantId == "" {
		return "", RequiredParametersMissingError
	}

	azureEndpoint := NewEndpoint(name, AzureEndpoint)
	azureEndpoint.EndpointProperties.PrivateKey = secretKey
	azureEndpoint.EndpointProperties.PrivateKeyId = accessKey
	azureEndpoint.EndpointProperties.RegionId = regionId
	azureEndpoint.EndpointProperties.UserLink = subscriptionId
	azureEndpoint.EndpointProperties.AzureTenantId = tenantId

	return processEndpointAddRequest(azureEndpoint)
}

func AddVsphereEndpoint(name, hostname, username, password, datacenter string) (string, error) {
	if hostname == "" || username == "" || password == "" {
		return "", RequiredParametersMissingError
	}

	vsphereEndpoint := NewEndpoint(name, VsphereEndpoint)
	vsphereEndpoint.EndpointProperties.PrivateKey = password
	vsphereEndpoint.EndpointProperties.PrivateKeyId = username
	vsphereEndpoint.EndpointProperties.RegionId = datacenter
	vsphereEndpoint.EndpointProperties.HostName = hostname

	return processEndpointAddRequest(vsphereEndpoint)
}

func processEndpointAddRequest(endpoint *Endpoint) (string, error) {
	jsonBody, err := json.Marshal(endpoint)
	utils.CheckBlockingError(err)

	url := config.URL + AddEndpointUrl
	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	req.Header.Set("Pragma", "xn-force-index-update")
	_, respBody, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return "", respErr
	}

	addedEndpoint := &Endpoint{}
	err = json.Unmarshal(respBody, addedEndpoint)
	utils.CheckBlockingError(err)

	return addedEndpoint.GetID(), nil
}

func RemoveEndpoint(id string) (string, error) {
	fullId, err := selflink.GetFullId(id, new(EndpointList), utils.ENDPOINT)
	utils.CheckBlockingError(err)

	url := config.URL + utils.CreateResLinkForEndpoint(fullId)
	req, _ := http.NewRequest("DELETE", url, nil)
	_, _, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return "", respErr
	}
	return id, nil
}

func getEndpoint(id string) (*Endpoint, error) {
	fullId, err := selflink.GetFullId(id, new(EndpointList), utils.ENDPOINT)
	utils.CheckBlockingError(err)

	url := config.URL + utils.CreateResLinkForEndpoint(fullId)
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return nil, respErr
	}

	endpoint := &Endpoint{}
	err = json.Unmarshal(respBody, endpoint)
	utils.CheckBlockingError(err)
	return endpoint, nil
}

func EditAwsEndpoint(id, name, accessKey, secretKey, regionId string) (string, error) {
	oldEndpoint, err := getEndpoint(id)
	if err != nil {
		return "", err
	}

	if name != "" {
		oldEndpoint.Name = name
	}
	if secretKey != "" {
		oldEndpoint.EndpointProperties.PrivateKey = secretKey
	}
	if accessKey != "" {
		oldEndpoint.EndpointProperties.PrivateKeyId = accessKey
	}
	if regionId != "" {
		oldEndpoint.EndpointProperties.RegionId = regionId
	}

	return processEndpointUpdateRequest(oldEndpoint)
}

func EditAzureEndpoint(id, name, accessKey, secretKey, regionId, subscriptionId, tenantId string) (string, error) {
	oldEndpoint, err := getEndpoint(id)
	if err != nil {
		return "", err
	}

	if name != "" {
		oldEndpoint.Name = name
	}
	if secretKey != "" {
		oldEndpoint.EndpointProperties.PrivateKey = secretKey
	}
	if accessKey != "" {
		oldEndpoint.EndpointProperties.PrivateKeyId = accessKey
	}
	if regionId != "" {
		oldEndpoint.EndpointProperties.RegionId = regionId
	}
	if subscriptionId != "" {
		oldEndpoint.EndpointProperties.UserLink = subscriptionId
	}
	if tenantId != "" {
		oldEndpoint.EndpointProperties.AzureTenantId = tenantId
	}

	return processEndpointUpdateRequest(oldEndpoint)
}

func EditVsphereEndpoint(id, name, hostname, username, password, datacenter string) (string, error) {
	oldEndpoint, err := getEndpoint(id)
	if err != nil {
		return "", err
	}

	if name != "" {
		oldEndpoint.Name = name
	}
	if password != "" {
		oldEndpoint.EndpointProperties.PrivateKey = password
	}
	if username != "" {
		oldEndpoint.EndpointProperties.PrivateKeyId = username
	}
	if datacenter != "" {
		oldEndpoint.EndpointProperties.RegionId = datacenter
	}
	if hostname != "" {
		oldEndpoint.EndpointProperties.HostName = hostname
	}
	return processEndpointUpdateRequest(oldEndpoint)
}

func processEndpointUpdateRequest(endpoint *Endpoint) (string, error) {
	jsonBody, err := json.Marshal(endpoint)
	utils.CheckBlockingError(err)

	url := config.URL + "/config/endpoints" + utils.CreateResLinkForEndpoint(endpoint.GetID())
	req, _ := http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
	req.Header.Set("Pragma", "xn-force-index-update")
	_, _, respErr := client.ProcessRequest(req)

	if respErr != nil {
		return "", respErr
	}

	return endpoint.GetID(), nil
}
