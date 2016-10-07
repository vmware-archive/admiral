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
	. "admiral/testutils"
)

func TestAddUseRemoveResourcePools(t *testing.T) {
	// Preparing the test.
	TestPrintln("Configuring the env.")
	config.GetCfg()
	tc, err := ConfigureTestEnv()
	CheckTestError(err, t)

	err = loginCmd.ParseFlags([]string{"--user=" + tc.Username, "--pass=" + tc.Password, "--url=" + tc.AdmiralAddress})
	CheckTestError(err, t)
	token := RunLogin([]string{})
	if token == "" {
		t.Error("Login failed.")
		t.FailNow()
	}

	// Run the test.
	TestPrintln("Adding new placement zone.")
	rpMsg, err := RunPlacementZoneAdd([]string{"test-placement-zone"})
	CheckTestError(err, t)
	rpId := strings.Split(rpMsg, " ")[3]

	TestPrintln("Adding new host with the new placement zone.")
	hostAddCmd.ParseFlags([]string{"--address=" + tc.HostAddress, "--placement-zone=" + rpId,
		"--public=" + tc.PublicKey, "--private=" + tc.PrivateKey, "--accept"})
	hostMsg, err := RunAddHost([]string{})
	CheckTestError(err, t)
	hostId := strings.Split(hostMsg, " ")[2]

	// Clean up the env.
	TestPrintln("Removing host.")
	hostRemoveCmd.ParseFlags([]string{"--force"})
	hostMsg, err = RunHostRemove([]string{hostId})
	CheckTestError(err, t)

	TestPrintln("Removing placement zone.")
	rpMsg, err = RunPlacementZoneRemove([]string{rpId})
	CheckTestError(err, t)
}
