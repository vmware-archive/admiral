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
	"strconv"
	"strings"
	"testing"
	"time"

	"admiral/config"
	. "admiral/testutils"
)

func TestPlacementAddRemove(t *testing.T) {
	testArguments := [][]string{}

	testArguments = append(
		testArguments, []string{"--placement-zone=default-placement-zone", "--priority=50",
			"--memory-limit=100mb", "--instances=10", "--cpuShares=2", "--project=", "--deployment-policy="})

	testArguments = append(
		testArguments, []string{"--placement-zone=default-placement-zone"})

	// Preparing the test
	TestPrintln("Configuring the env.")
	config.GetCfg()
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

	TestPrintln("Adding new project.")
	projectAddCmd.ParseFlags([]string{"--description=test-description"})
	projectMsg, err := RunProjectAdd([]string{"test-project"})
	CheckTestError(err, t)
	projectId := strings.Split(projectMsg, " ")[2]

	TestPrintln("Addning new deployment policy.")
	deploymentPolicyAddCmd.ParseFlags([]string{"--description=test-dp-description"})
	dpMsg, err := RunDeploymentPolicyAdd([]string{"test-deployment-policy"})
	CheckTestError(err, t)
	dpId := strings.Split(dpMsg, " ")[3]

	// Setting up IDs
	testArguments[0][5] += projectId
	testArguments[0][6] += dpId

	// Run the test.
	for i := range testArguments {
		time.Sleep(5 * time.Second) // Wait for data collection.
		TestPrintln("Adding new placement.")
		ResetFlagValues(placementAddCmd)
		placementAddCmd.ParseFlags(testArguments[i])
		placementMsg, err := RunPlacementAdd([]string{"test-placement-" + strconv.Itoa(i)})
		CheckTestError(err, t)
		placementId := strings.Split(placementMsg, " ")[2]
		TestPrintln("Removing the placement")
		placementMsg, err = RunPlacementRemove([]string{placementId})
		CheckTestError(err, t)
	}

	// Clean up the env.
	TestPrintln("Removing the project.")
	projectMsg, err = RunProjectRemove([]string{projectId})
	CheckTestError(err, t)

	TestPrintln("Removing the deployment policy.")
	dpMsg, err = RunDeploymentPolicyRemove([]string{dpId})
	CheckTestError(err, t)

	TestPrintln("Removing the host.")
	hostMsg, err = RunHostRemove([]string{hostId})
	CheckTestError(err, t)
}
