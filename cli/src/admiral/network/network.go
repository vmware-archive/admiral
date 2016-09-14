package network

import (
	"bytes"
	"fmt"
	"os"
	"regexp"
	"strings"
)

type Network struct {
	Name               string              `json:"name"`
	IPAMConfigurations []map[string]string `json:"ipamConfigurations"`
	Options            map[string]string   `json:"options"`
}

func (n *Network) SetName(name string) {
	if name == "" {
		fmt.Println("Network name cannot be empty.")
		os.Exit(0)
	}
	n.Name = name
}

func (n *Network) SetIPAMConfig(subnets, gateways []string) {
	sCount := len(subnets)
	gCount := len(gateways)
	ipamConfig := make([]map[string]string, 0)

	var bigger int

	if sCount >= gCount {
		bigger = sCount
	} else {
		bigger = gCount
	}

	for i := 0; i < bigger; i++ {
		tempMap := make(map[string]string)
		if i < sCount && i < gCount {
			tempMap["subnetCIDR"] = subnets[i]
			tempMap["gateway"] = gateways[i]

		} else if i < sCount {
			tempMap["subnetCIDR"] = subnets[i]
		} else if i < gCount {
			tempMap["gateway"] = gateways[i]
		} else {
			break
		}
		ipamConfig = append(ipamConfig, tempMap)
	}
	n.IPAMConfigurations = ipamConfig
}

func (n *Network) SetOptions(options []string) {
	optMap := make(map[string]string)
	regex, _ := regexp.Compile("\\w+\\s*\\:\\s*\\w+")
	for _, optPair := range options {
		if !regex.MatchString(optPair) {
			continue
		}
		optPairArr := strings.Split(optPair, ":")
		optMap[optPairArr[0]] = optPairArr[1]
	}
	n.Options = optMap
}

func (n *Network) String() string {
	var buffer bytes.Buffer
	buffer.WriteString("Name: " + n.Name + "\n")
	buffer.WriteString("IPAM Configurations:\n")
	for _, config := range n.IPAMConfigurations {
		for key, val := range config {
			pair := fmt.Sprintf("    %s : %s\n", key, val)
			buffer.WriteString(pair)
		}
	}
	buffer.WriteString("Options:\n")
	for key, val := range n.Options {
		pair := fmt.Sprintf("    %s : %s\n", key, val)
		buffer.WriteString(pair)
	}
	return buffer.String()
}

type NetworkList struct {
	DocumentLinks []string           `json:"documentLinks"`
	Documents     map[string]Network `json:"documents"`
}
