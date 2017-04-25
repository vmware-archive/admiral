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
	"admiral/utils"
)

func TestApplicationProvision(t *testing.T) {
	// Preparing the test
	TestPrintln("Configuring the env.")
	utils.IsTest = true
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

	TestPrintln("Importing template.")
	templateMsg, err := RunTemplateImport([]string{"../testdata/wordpress.yaml"})
	CheckTestError(err, t)
	templateId := strings.Split(templateMsg, " ")[2]

	// Run the test
	TestPrintln("Provisioning application.")
	appRunCmd.ParseFlags([]string{"--timeout=180"})
	appMsg, err := RunAppRun([]string{templateId})
	CheckTestError(err, t)
	appId := strings.Split(appMsg, " ")[2]

	TestPrintln("Restarting the application.")
	appMsg, err = RunAppRestart([]string{appId})
	CheckTestError(err, t)

	TestPrintln("Removing the application.")
	appMsg, err = RunAppRemove([]string{appId})
	CheckTestError(err, t)

	// Clean up env
	TestPrintln("Removing the host.")
	hostRemoveCmd.ParseFlags([]string{"--force"})
	hostMsg, err = RunHostRemove([]string{hostId})
	CheckTestError(err, t)
	err = hosts.ValidateHostIsDeleted(hostId)
	CheckTestError(err, t)

	TestPrintln("Removing the template.")
	templateMsg, err = RunTemplateRemove([]string{templateId})
	CheckTestError(err, t)

}
