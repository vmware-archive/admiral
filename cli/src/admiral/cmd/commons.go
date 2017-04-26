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

	"admiral/common/utils"
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
	newNameDesc    = "New name."
	newDescription string
	newAddress     string
	newCred        string

	//If true print all containers.
	allContainers     bool
	allContainersDesc = "Show all containers."

	//Container Run Command Flags
	clusterSize       int32
	clusterSizeDesc   = "The number of nodes to be provisioned."
	cmds              []string
	cmdsDesc          = "Commands to run on container start."
	containerName     string
	containerNameDesc = "Container name."
	envVariables      []string
	envVariablesDesc  = "Set enivornment variables."
	hostName          string
	hostNameDesc      = "Container host name."
	retryCount        int32
	retryCountDesc    = "Max restart count on container failures."
	logDriver         string
	logDriverDesc     = "Logging driver for container."
	memoryLimit       int64
	memorySwap        int64
	memorySwapDesc    = "Total memory limit(Memory + Swap), set -1 to disable swap"
	networkMode       string
	networkModeDesc   = "Sets the networking mode for the container."
	ports             []string
	portsDesc         = "Publish a container's port(s) to the host. Format: hostPort:containerPort"
	publishAll        bool
	publishAllDesc    = "Publish all exposed ports to random ports."
	restartPol        string
	restartPolDesc    = "Restart policy to apply."
	workingDir        string
	workingDirDesc    = "Working directory inside the container"
	volumes           []string
	volumesDesc       = "Bind mount volume"

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

	// Create host flags
	endpointId       string
	endpointIdDesc   = "Endpoint ID."
	hostOS           string
	hostOSDesc       = "Host OS. Use \"admiral host create [type] --help\" to list available host OS images."
	instanceType     string
	instanceTypeDesc = "Instance type ID. Use \"admiral host create [type] --help\" to list available instance types."
	guestCred        string
	guestCredDesc    = "Guest credentials ID."
	destination      string
	destinationDesc  = "Destination ID. Use \"admiral host create vsphere --help\" to list available destinations on vsphere endpoints."

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
	instances          int64
	instancesDesc      = "Instances."
	priority           string
	priorityDesc       = "Priority."
	memoryLimitStr     string
	memoryLimitDesc    = "Memory limit. Default unit: kb. Units supported: kb/mb/gb. Example: 1024mb"
	priorityInt        int32
	maxNumberInstances int64
	cpuSharesInt       int64

	outputFormat     string
	outputFormatDesc = "Output format: json, table."

	//Business groups flags
	businessGroupId     string
	businessGroupIdDesc = "Business group ID."

	//Encryption flags
	encryptionKey string

	//Tags flags
	tags                    []string
	tagsDesc                = "Tags. Input format - key:value"
	tagsToRemove            []string
	tagsToRemoveDesc        = "Tags. Input format - key:value"
	tagsToMatch             []string
	tagsToMatchDesc         = "Tags to match. Input format - key:value"
	tagsToMatchToRemove     []string
	tagsToMatchToRemoveDesc = "Tags to match. Input format - key:value"

	//Endpoint flags
	accessKey            string
	accessKeyDesc        = "Access key ID."
	secretKey            string
	secretKeyDesc        = "Secret access key."
	regionId             string
	regionIdDesc         = "Region ID."
	subscriptionId       string
	subscriptionIdDesc   = "Subscription ID."
	endpointTenantId     string
	endpointTenantIdDesc = "Tenant ID."
	endpointUsername     string
	endpointUsernameDesc = "Username."
	endpointPassword     string
	endpointPasswordDesc = "Password."
	endpointHostName     string
	endpointHostNameDesc = "Host name or IP."
	datacenterName       string
	datacenterNameDesc   = "Datacenter name."
	dockerPort           int
	dockerPortDesc       = "Docker host port."

	//custom timeout flags
	customTimeout     int
	customTimeoutDesc = "Set custom task timeout (seconds)."
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
		fmt.Fprint(os.Stderr, err)
		os.Exit(1)
	}
	if utils.Quiet {
		quietOutput := makeQuietOutput(output)
		if quietOutput == "" {
			os.Exit(0)
		}
		fmt.Println(makeQuietOutput(output))
		os.Exit(0)
	}
	fmt.Println(output)
}

func processOutputMultiErrors(output string, errs []error) {
	var buffer bytes.Buffer
	for _, err := range errs {
		if err != nil {
			buffer.WriteString(err.Error() + "\n")
		}
	}
	if buffer.String() != "" {
		fmt.Fprintln(os.Stderr, strings.TrimSpace(buffer.String()))
		os.Exit(1)
	}

	if utils.Quiet {
		quietOutput := makeQuietOutput(output)
		if quietOutput == "" {
			os.Exit(0)
		}
		fmt.Println(makeQuietOutput(output))
		os.Exit(0)
	}
	fmt.Println(output)
}

func formatAndPrintOutput(output string, err error) {
	writer := tabwriter.NewWriter(os.Stdout, 5, 0, 5, ' ', 0)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
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

func makeQuietOutput(output string) string {
	if !strings.Contains(output, ": ") {
		return output
	}
	outputArr := strings.Split(output, ": ")
	if len(outputArr) < 2 {
		return ""
	}
	return outputArr[1]
}
