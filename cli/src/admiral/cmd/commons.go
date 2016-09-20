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

	//Flag for verbose option.
	verbose bool

	//Flag to execute commands by enitity's self link, in order to avoid duplicates.
	//selfID     string
	//selfIDDesc string = "Executing command by ID will avoid duplicate names conflict."

	//Flag to store custom properties.
	custProps     []string
	custPropsDesc string = "Add some custom properties"

	//Flag used to update.
	newName        string
	newDescription string
	newAddress     string
	newCred        string

	//Flag for group.
	groupID     string
	groupIDDesc string = "Group ID."

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
	//Container Run Command Flags

	//Count for clusters
	scaleCount int32
	//Count for clusters

	//Credentials flags
	credName    string
	publicCert  string
	privateCert string
	userName    string
	passWord    string

	publicCertDesc  string = "Location to your public key."
	privateCertDesc string = "Location to your private key."
	//Credentials flags

	//Custom Properties flags
	cpHostIP    string
	cpCredID    string
	cpResPoolID string
	cpKeys      []string
	cpVals      []string
	//Custom Properties flags

	//Deployment policy flags
	dpName        string
	dpDescription string
	//Deployment policy flags

	//Group description flag
	groupDescription string

	//Host flags
	ipF         string
	resPoolF    string
	deplPolicyF string
	autoAccept  bool
	//Host flags
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
