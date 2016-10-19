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
	"fmt"
	"os"
	"text/tabwriter"
)

var (
	//Flag to wait for task
	asyncTask bool
	asyncDesc string = "Wait until the task is finished."

	//Used to specify format of exported app.
	formatTemplate string

	//Used to specify file.
	dirF string

	//Flag to decide to list included containers
	inclCont bool

	//Flag for query.
	queryF string

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
	custPropsDesc string = "Add custom properties. Format: key=value"

	//Flag used to update.
	newName        string
	newDescription string
	newAddress     string
	newCred        string

	//If true print all containers.
	allContainers bool

	//Container Run Command Flags
	clusterSize int32
	cmds        []string
	env         []string
	hostName    string
	retryCount  int32
	logDriver   string
	memory      int64
	memorySwap  int64
	networkMode string
	ports       []string
	publishAll  bool
	restartPol  string
	workingDir  string
	volumes     []string

	//Count for clusters
	scaleCount int32

	//Credentials flags
	credName    string
	publicCert  string
	privateCert string
	userName    string
	passWord    string

	publicCertDesc  string = "Location to your public key."
	privateCertDesc string = "Location to your private key."

	//Custom Properties flags
	cpHostIP    string
	cpCredID    string
	cpResPoolID string
	cpKeys      []string
	cpVals      []string

	//Deployment policy flags
	dpName        string
	dpDescription string

	//Project description flag
	projectDescription string

	//Host flags
	ipF             string
	placementZoneID string
	deplPolicyF     string
	autoAccept      bool

	// Network flags
	gateways      []string
	subnets       []string
	options       []string
	ipranges      []string
	hostAddresses []string
	networkDriver string
	ipamDriver    string
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

func formatAndPrintOutput(output string, err error) {
	writer := tabwriter.NewWriter(os.Stdout, 5, 0, 5, ' ', 0)
	if err != nil {
		fmt.Fprintln(writer, err)
	} else {
		fmt.Fprintln(writer, output)
	}
	writer.Flush()
}
