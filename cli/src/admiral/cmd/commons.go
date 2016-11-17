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

package cmd

import (
	"bytes"
	"fmt"
	"os"
	"strings"
	"text/tabwriter"

	"admiral/utils"
)

var (
	//Prefixes
	required    = "*Required* "
	prefixNew   = "New "
	vraOptional = "*vRA* "

	//Token flag description
	tokenDesc = "Authorization token."

	//Flag to wait for task
	asyncTask bool
	asyncDesc string = "Wait until the task is finished."

	//Used to specify format of exported app.
	formatTemplate     string
	formatTemplateDesc = "File format - vRA/Docker"

	//Used to specify file.
	dirF string

	//Used when provision app from file.
	keepTemplate     bool
	keepTemplateDesc = "Do not remove template after provisioning."

	//Flag to decide to list included containers
	inclCont     bool
	inclContDesc = "Lists containers inside the template."

	//Flag for query.
	queryF     string
	queryFDesc = "Add query."

	//Flag for url
	urlF string

	//Flag to force remove.
	forceF    bool
	forceDesc string = "Force remove."

	//Flag for proejct.
	projectF     string
	projectFDesc string = "Project ID."

	//Flag to store custom properties.
	custProps     []string
	custPropsDesc = "Add custom properties. Format: key=value"

	//Flag used to update.
	newName        string
	newDescription string
	newAddress     string
	newCred        string

	//If true print all containers.
	allContainers     bool
	allContainersDesc = "Show all containers."

	//Container Run Command Flags
	clusterSize      int32
	clusterSizeDesc  = "The number of nodes to be provisioned."
	cmds             []string
	cmdsDesc         = "Commands to run on container start."
	envVariables     []string
	envVariablesDesc = "Set enivornment variables."
	hostName         string
	hostNameDesc     = "Container host name."
	retryCount       int32
	retryCountDesc   = "Max restart count on container failures."
	logDriver        string
	logDriverDesc    = "Logging driver for container."
	memoryLimit      int64
	memorySwap       int64
	memorySwapDesc   = "Total memory limit(Memory + Swap), set -1 to disable swap"
	networkMode      string
	networkModeDesc  = "Sets the networking mode for the container."
	ports            []string
	portsDesc        = "Publish a container's port(s) to the host."
	publishAll       bool
	publishAllDesc   = "Publish all exposed ports to random ports."
	restartPol       string
	restartPolDesc   = "Restart policy to apply."
	workingDir       string
	workingDirDesc   = "Working directory inside the container"
	volumes          []string
	volumesDesc      = "Bind mount volume"

	//Count for clusters
	scaleCount     int32
	scaleCountDesc = "Nodes count of the resource."

	//Credentials flags
	credId          string
	credIdDesc      = "Credentials ID."
	publicCert      string
	publicCertDesc  = "Location to your public key."
	privateCert     string
	privateCertDesc = "Location to your private key."
	userName        string
	userNameDesc    = "Username."
	passWord        string
	passWordDesc    = "Password."

	//Custom Properties flags
	cpHostId     string
	cpHostIdDesc = "ID of the host that you want to manage custom properties."
	cpCredId     string
	cpCredIdDesc = "ID of the credentials that you want to manage custom properties."
	cpPzId       string
	cpPzIdDesc   = "ID of the placement zone that you want to manage custom properties."
	cpKeys       []string
	cpKeysDesc   = "Keys of custom properties."
	cpVals       []string
	cpValsDesc   = "Values of custom properties"

	//Deployment policy flags
	dpName            string
	dpNameDesc        = "New deployment policy name."
	dpDescription     string
	dpDescriptionDesc = "Deployment policy description."

	//Project description flag
	projectDescription     string
	projectDescriptionDesc = "Project description."

	//Host flags
	ipF                 string
	ipFDesc             = "Address of host."
	placementZoneId     string
	placementZoneIdDesc = "Placement zone ID."
	deplPolicyF         string
	deplPolicyFDesc     = "DeploymentPolicy ID."
	autoAccept          bool
	autoAcceptDesc      = "Auto accept if certificate is not trusted."

	// Network flags
	gateways          []string
	gatewaysDesc      = "Gateway for the master subnet."
	subnets           []string
	subnetsDesc       = "Subnet in CIDR format that represents a network segment."
	ipRanges          []string
	ipRangesDesc      = "IP Range"
	hostIds           []string
	hostIdsDesc       = "Hosts IDs."
	networkDriver     string
	networkDriverDesc = "Driver to manage the Network."
	ipamDriver        string
	ipamDriverDesc    = "IPAM driver."

	// Config flags
	keyProp     string
	keyPropDesc = "Key"
	valProp     string
	valPropDesc = "Value"

	// Events/requests flag
	clearAll           bool
	clearAllReqDesc    = "Clear all logged requests."
	clearAllEventsDesc = "Clear all logged events."

	// Placements flags
	cpuShares          string
	cpuSharesDesc      = "CPU shares."
	instances          string
	instancesDesc      = "Instances."
	priority           string
	priorityDesc       = "Priority."
	memoryLimitStr     string
	memoryLimitDesc    = "Memory limit. Default unit: kb. Units supported: kb/mb/gb. Example: 1024mb"
	priorityInt        int32
	maxNumberInstances int32
	cpuSharesInt       int32

	outputFormat     string
	outputFormatDesc = "Output format: json, table."

	//Business groups flags
	businessGroupId     string
	businessGroupIdDesc = "Business group ID."

	//Encryption flags
	encryptionKey string
)

var admiralLogo = `
       *****
     ***###***           @@      @@@@    @      @  @  @@@@       @@     @
   ******#******         @@      @   @   @@    @@  @  @   @      @@     @
   ****#*#*#****        @  @     @    @  @ @  @ @  @  @    @    @  @    @
   *****###*****        @  @     @    @  @ @  @ @  @  @   @     @  @    @
    ***********         @  @     @    @  @  @@  @  @  @@@@@     @  @    @
    *         *        @@@@@@    @    @  @      @  @  @  @     @@@@@@   @
    ***********       @      @   @   @   @      @  @  @   @    @    @   @
    ***********      @        @  @@@@    @      @  @  @    @  @      @  @@@@@
      *******

                             Github: https://github.com/vmware/admiral
                             Wiki: https://github.com/vmware/admiral/wiki
                             Version: %s
`

func ValidateArgsCount(args []string) (string, bool) {
	if len(args) > 0 {
		return args[0], true
	}
	return "", false
}

func processOutput(output string, err error) {
	if err != nil {
		fmt.Println(err)
	} else {
		fmt.Println(output)
	}
}

func processOutputMultiErrors(output string, errs []error) {
	var buffer bytes.Buffer
	for _, err := range errs {
		if err != nil {
			buffer.WriteString(err.Error() + "\n")
		}
	}
	if buffer.String() == "" {
		fmt.Println(output)
	} else {
		fmt.Println(strings.TrimSpace(buffer.String()))
	}
}

func formatAndPrintOutput(output string, err error) {
	writer := tabwriter.NewWriter(os.Stdout, 5, 0, 5, ' ', 0)
	if err != nil {
		fmt.Fprintln(writer, err)
	} else {
		fmt.Fprintln(writer, output)
	}
	writer.Flush()
}

func checkForErrors(errs []error) []error {
	notNilErrs := make([]error, 0)
	for _, err := range errs {
		if err != nil {
			notNilErrs = append(notNilErrs, err)
		}
	}
	return notNilErrs
}

func isVraMode() bool {
	token, _ := utils.GetAuthToken()
	if strings.Contains(token, "Bearer") {
		return true
	}
	return false
}

func urlRemoveTrailingSlash(url string) string {
	newUrl := []rune(url)
	if strings.HasSuffix(url, "/") {
		newUrl = newUrl[0 : len(newUrl)-1]
	}
	return string(newUrl)
}
