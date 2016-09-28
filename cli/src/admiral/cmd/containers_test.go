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
	"strings"
	"testing"

	"admiral/config"
)

//TestContainerProvision will add host and credentials,
//it will provision container, stop it, remove it and remove the host.
func TestContainerProvision(t *testing.T) {
	// Preparing the test.
	testPrintln("Configuring the env.")
	config.GetCfg()
	tc, err := configureTestEnv()
	CheckTestError(err, t)

	testPrintln("Login and adding host.")
	hostMsg := loginAndAddHost(tc, t)
	hostId := strings.Split(hostMsg, " ")[2]

	// Run the test
	testPrintln("Provisioning container.")
	contMsg, err := RunContainerRun([]string{"kitematic/hello-world-nginx"})
	CheckTestError(err, t)

	testPrintln("Stopping container.")
	contId := strings.Split(contMsg, " ")[2]
	contMsg, err = RunContainerStop([]string{contId})
	CheckTestError(err, t)

	testPrintln("Removing container.")
	contMsg, err = RunContainersRemove([]string{contId})
	CheckTestError(err, t)

	// Clean up env.
	testPrintln("Removing host.")
	hostRemoveCmd.ParseFlags([]string{"--force"})
	hostMsg, err = RunHostRemove([]string{hostId})
	CheckTestError(err, t)
}
