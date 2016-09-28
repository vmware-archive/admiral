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
	"testing"

	"admiral/config"
	"strings"
)

func TestAddUseRemoveResourcePools(t *testing.T) {
	// Preparing the test.
	testPrintln("Configuring the env.")
	config.GetCfg()
	tc, err := configureTestEnv()
	CheckTestError(err, t)

	err = loginCmd.ParseFlags([]string{"--user=" + tc.Username, "--pass=" + tc.Password, "--url=" + tc.AdmiralAddress})
	CheckTestError(err, t)
	token := RunLogin([]string{})
	if token == "" {
		t.Error("Login failed.")
		t.FailNow()
	}

	// Run the test.
	testPrintln("Adding new resource pool.")
	rpMsg, err := RunResourcePoolAdd([]string{"test-resource-pool"})
	CheckTestError(err, t)
	rpId := strings.Split(rpMsg, " ")[3]

	testPrintln("Adding new host with the new resource pool.")
	hostAddCmd.ParseFlags([]string{"--ip=" + tc.HostAddress, "--resource-pool=" + rpId,
		"--public=" + tc.PublicKey, "--private=" + tc.PrivateKey, "--accept"})
	hostMsg, err := RunAddHost([]string{})
	CheckTestError(err, t)
	hostId := strings.Split(hostMsg, " ")[2]

	// Clean up the env.
	testPrintln("Removing host.")
	hostRemoveCmd.ParseFlags([]string{"--force"})
	hostMsg, err = RunHostRemove([]string{hostId})
	CheckTestError(err, t)

	testPrintln("Removing resource pool.")
	rpMsg, err = RunResourcePoolRemove([]string{rpId})
	CheckTestError(err, t)
}
