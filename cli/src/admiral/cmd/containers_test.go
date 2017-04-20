// +build e2e

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
	"admiral/hosts"
	. "admiral/testutils"
)

//TestContainerProvision will add host and credentials,
//it will provision container, stop it, remove it and remove the host.
func TestContainerProvision(t *testing.T) {
	// Preparing the test.
	TestPrintln("Configuring the env.")
	config.GetCfgForTests()
	tc, err := ConfigureTestEnv()
	CheckTestError(err, t)

	TestPrintln("Login")
	err = loginCmd.ParseFlags([]string{"--user=" + tc.Username, "--pass=" + tc.Password, "--url=" + tc.AdmiralAddress})
	CheckTestError(err, t)
	token, _ := RunLogin([]string{})
	if token == "" {
		t.Error("Login failed.")
		t.FailNow()
	}

	TestPrintln("Adding host.")
	hostAddCmd.ParseFlags([]string{"--address=" + tc.HostAddress, "--placement-zone=" + tc.PlacementZone,
		"--public=" + tc.PublicKey, "--private=" + tc.PrivateKey, "--accept"})
	hostMsg, err := RunAddHost([]string{})
	CheckTestError(err, t)
	hostId := strings.Split(hostMsg, " ")[2]

	// Run the test
	TestPrintln("Provisioning container.")
	contMsg, errs := RunContainerRun([]string{"kitematic/hello-world-nginx"})
	CheckTestErrors(errs, t)

	TestPrintln("Stopping container.")
	contId := strings.Split(contMsg, " ")[2]
	contMsg, err = RunContainerStop([]string{contId})
	CheckTestError(err, t)

	TestPrintln("Removing container.")
	contMsg, err = RunContainersRemove([]string{contId})
	CheckTestError(err, t)

	// Clean up env.
	TestPrintln("Removing host.")
	hostRemoveCmd.ParseFlags([]string{"--force"})
	hostMsg, err = RunHostRemove([]string{hostId})
	CheckTestError(err, t)
	err = hosts.ValidateHostIsDeleted(hostId)
	CheckTestError(err, t)
}
