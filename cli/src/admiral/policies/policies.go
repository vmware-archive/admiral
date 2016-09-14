package policies

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"

	"admiral/client"
	"admiral/config"
	"admiral/deplPolicy"
	"admiral/functions"
	"admiral/groups"
	"admiral/resourcePools"
)

type Policy struct {
	Name                    string   `json:"name"`
	ResourcePoolLink        string   `json:"resourcePoolLink"`
	Priority                int32    `json:"priority"`
	ResourceType            string   `json:"resourceType"`
	MaxNumberInstances      int32    `json:"maxNumberInstances"`
	MemoryLimit             int64    `json:"memoryLimit"`
	StorageLimit            int32    `json:"storageLimit"`
	CpuShares               int32    `json:"cpuShares"`
	DeploymentPolicyLink    string   `json:"deploymentPolicyLink"`
	AvailableInstancesCount int32    `json:"availableInstancesCount"`
	AvailableMemory         int32    `json:"availableMemory"`
	TenantLinks             []string `json:"tenantLinks"`
	DocumentSelfLink        *string  `json:"documentSelfLink"`
	DocumentKind            string   `json:"documentKind,omitempty"`
}

func (p *Policy) GetID() string {
	return strings.Replace(*p.DocumentSelfLink, "/resources/group-policies/", "", -1)
}

type PolicyToAdd struct {
	Name                 string   `json:"name,omitempty"`
	ResourcePoolLink     string   `json:"resourcePoolLink,omitempty"`
	Priority             string   `json:"priority,omitempty"`
	ResourceType         string   `json:"resourceType,omitempty"`
	MaxNumberInstances   string   `json:"maxNumberInstances,omitempty"`
	MemoryLimit          int64    `json:"memoryLimit,omitmepty"`
	StorageLimit         string   `json:"storageLimit,omitempty"`
	CpuShares            string   `json:"cpuShares,omitempty"`
	DeploymentPolicyLink string   `json:"deploymentPolicyLink,omitempty"`
	TenantLinks          []string `json:"tenantLinks,omitempty"`
	DocumentKind         string   `json:"documentKind,omitempty"`

	AvailableInstancesCount      int32            `json:"availableInstancesCount,omitempty"`
	AllocatedInstancesCount      int32            `json:"allocatedInstancesCount"`
	AvailableMemory              int32            `json:"availableMemory,omitempty"`
	ResourceQuotaPerResourceDesc map[string]int32 `json:"resourceQuotaPerResourceDesc"`
}

type PolicyToUpdate struct {
	Name                 string   `json:"name,omitempty"`
	ResourcePoolLink     string   `json:"resourcePoolLink,omitempty"`
	Priority             int32    `json:"priority,omitempty"`
	ResourceType         string   `json:"resourceType,omitempty"`
	MaxNumberInstances   int32    `json:"maxNumberInstances,omitempty"`
	MemoryLimit          int64    `json:"memoryLimit,omitmepty"`
	StorageLimit         int32    `json:"storageLimit,omitempty"`
	CpuShares            int32    `json:"cpuShares,omitempty"`
	DeploymentPolicyLink string   `json:"deploymentPolicyLink,omitempty"`
	TenantLinks          []string `json:"tenantLinks,omitempty"`
	DocumentKind         string   `json:"documentKind,omitempty"`

	AvailableInstancesCount      int32            `json:"availableInstancesCount,omitempty"`
	AllocatedInstancesCount      int32            `json:"allocatedInstancesCount"`
	AvailableMemory              int32            `json:"availableMemory,omitempty"`
	ResourceQuotaPerResourceDesc map[string]int32 `json:"resourceQuotaPerResourceDesc"`
}

type PolicyList struct {
	DocumentLinks []string          `json:"documentLinks"`
	Documents     map[string]Policy `json:"documents"`
}

func (pl *PolicyList) FetchPolices() int {
	url := config.URL + "/resources/group-policies?expand"

	req, _ := http.NewRequest("GET", url, nil)
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	err := json.Unmarshal(respBody, pl)
	functions.CheckJson(err)
	return len(pl.DocumentLinks)
}

func (pl *PolicyList) Print() {
	if len(pl.DocumentLinks) < 1 {
		fmt.Println("n/a")
		return
	}
	fmt.Printf("%-40s %-25s %-25s %-25s %-20s %-15s %-15s %-15s %-15s \n",
		"ID", "NAME", "GROUP", "RESOURCE POOL", "DEPLOYMENT POLICY", "PRIORITY", "INSTANCES", "CPU SHARES", "MEMORY LIMIT")
	for _, link := range pl.DocumentLinks {
		val := pl.Documents[link]
		var (
			rp    string
			dp    string
			group string
		)

		if strings.TrimSpace(val.ResourcePoolLink) == "" {
			rp = ""
		} else {
			rp = resourcePools.GetRPName(val.ResourcePoolLink)
		}

		if strings.TrimSpace(val.DeploymentPolicyLink) == "" {
			dp = ""
		} else {
			dp = deplPolicy.GetDPName(val.DeploymentPolicyLink)
		}

		if len(val.TenantLinks) < 1 {
			group = ""
		} else {
			group = groups.GetGroupName(val.TenantLinks[0])
		}

		fmt.Printf("%-40s %-25s %-25s %-25s %-20s %-15d %-15d %-15d %-15d\n",
			val.GetID(), val.Name, group, rp, dp, val.Priority, val.AvailableInstancesCount,
			val.CpuShares, val.MemoryLimit)
	}
}

func RemovePolicy(polName string) (string, error) {
	polLinks := GetPolLinks(polName)
	if len(polLinks) > 1 {
		return "", errors.New("Policy with duplicate name found, provide ID to remove specific policy.")
	}
	if len(polLinks) < 1 {
		return "", errors.New("Policy not found.")
	}
	id := functions.GetResourceID(polLinks[0])
	return RemovePolicy(id)
}

func RemovePolicyID(id string) (string, error) {
	link := functions.CreateResLinkForPolicy(id)
	url := config.URL + link
	req, _ := http.NewRequest("DELETE", url, nil)
	resp, _ := client.ProcessRequest(req)
	if resp.StatusCode != 200 {
		return "", errors.New("Error occured when removing policy.")
	}
	return id, nil
}

func AddPolicy(namePol, cpuShares, instances, priority, groupID, resPoolID, deplPolID string, memoryLimit int64) (string, error) {
	url := config.URL + "/resources/group-policies"
	var (
		dpLink    string
		rpLink    string
		groupLink string
	)

	if !haveNeeded(deplPolID, instances, namePol, resPoolID, groupID) {
		return "", errors.New("--deployment-policy, --instances, --name, --resource-pool, --group parameters are must!")
	}

	dpLink = functions.CreateResLinkForDP(deplPolID)

	rpLink = functions.CreateResLinkForRP(resPoolID)

	groupLink = functions.CreateResLinkForGroup(groupID)

	policy := PolicyToAdd{
		//Must
		Name:                 namePol,
		MaxNumberInstances:   instances,
		ResourcePoolLink:     rpLink,
		DeploymentPolicyLink: dpLink,
		TenantLinks:          []string{groupLink},
		//Optional
		CpuShares:   cpuShares,
		MemoryLimit: memoryLimit,
		Priority:    priority,
	}

	jsonBody, err := json.Marshal(policy)
	functions.CheckJson(err)

	req, _ := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return "", errors.New("Error occured when adding policy.")
	}
	newPolicy := &Policy{}
	err = json.Unmarshal(respBody, newPolicy)
	functions.CheckJson(err)
	return newPolicy.GetID(), nil
}

func EditPolicy(name, namePol, groupID, resPoolID, deplPolID string, cpuShares, instances, priority int32, memoryLimit int64) (string, error) {
	polLinks := GetPolLinks(name)
	if len(polLinks) > 1 {
		return "", errors.New("Policy with duplicate name found, provide ID to remove specific policy.")
	}
	if len(polLinks) < 1 {
		return "", errors.New("Policy not found.")
	}

	id := functions.GetResourceID(polLinks[0])
	return EditPolicyID(id, namePol, groupID, resPoolID, deplPolID, cpuShares, instances, priority, memoryLimit)
}

func EditPolicyID(id, namePol, groupID, resPoolID, deplPolID string, cpuShares, instances, priority int32, memoryLimit int64) (string, error) {
	url := config.URL + functions.CreateResLinkForPolicy(id)
	//Workaround
	oldPolicy := &PolicyToUpdate{}
	req, _ := http.NewRequest("GET", url, nil)
	_, respBody := client.ProcessRequest(req)
	err := json.Unmarshal(respBody, oldPolicy)
	functions.CheckJson(err)
	//Workaround

	if cpuShares != -1 {
		oldPolicy.CpuShares = cpuShares
	}
	if instances != -1 {
		oldPolicy.MaxNumberInstances = instances
	}
	if namePol != "" {
		oldPolicy.Name = namePol
	}
	if priority != -1 {
		oldPolicy.Priority = priority
	}
	if groupID != "" {
		groupLinkIndex := GetGroupLinkIndex(oldPolicy.TenantLinks)
		groupLink := functions.CreateResLinkForGroup(groupID)
		oldPolicy.TenantLinks[groupLinkIndex] = groupLink
	}
	if resPoolID != "" {
		oldPolicy.ResourcePoolLink = functions.CreateResLinkForRP(resPoolID)
	}
	if deplPolID != "" {
		oldPolicy.DeploymentPolicyLink = functions.CreateResLinkForDP(deplPolID)
	}
	if memoryLimit != 0 {
		oldPolicy.MemoryLimit = 0
	}

	jsonBody, err := json.Marshal(oldPolicy)
	functions.CheckJson(err)
	req, _ = http.NewRequest("PUT", url, bytes.NewBuffer(jsonBody))
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return "", errors.New("Error occured when updating policy.")
	}
	newPolicy := &Policy{}
	err = json.Unmarshal(respBody, newPolicy)
	functions.CheckJson(err)
	return newPolicy.GetID(), nil
}

func haveNeeded(deplPolName, instances, namePol, resPoolName, tenants string) bool {
	if deplPolName == "" {
		return false
	}
	if instances == "" {
		return false
	}
	if namePol == "" {
		return false
	}
	if resPoolName == "" {
		return false
	}
	if tenants == "" {
		return false
	}
	return true
}

func GetPolLinks(name string) []string {
	pl := &PolicyList{}
	pl.FetchPolices()
	links := make([]string, 0)
	for key, val := range pl.Documents {
		if name == val.Name {
			links = append(links, key)
		}
	}
	return links
}

func GetGroupLinkIndex(tenantLinks []string) int {
	for i := range tenantLinks {
		if strings.Contains(tenantLinks[i], "/resources/groups/") {
			return i
		}
	}
	return -1
}
