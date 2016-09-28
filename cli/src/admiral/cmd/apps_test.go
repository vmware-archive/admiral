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

func TestApplicationProvision(t *testing.T) {
	t.SkipNow()
	// Preparing the test
	testPrintln("Configuring the env.")
	config.GetCfg()
	tc, err := configureTestEnv()
	CheckTestError(err, t)

	testPrintln("Login and adding host.")
	hostMsg := loginAndAddHost(tc, t)
	hostId := strings.Split(hostMsg, " ")[2]

	testPrintln("Importing template.")
	templateMsg, err := RunTemplateImport([]string{"../testdata/wordpress.yaml"})
	CheckTestError(err, t)
	templateId := strings.Split(templateMsg, " ")[2]

	// Run the test
	testPrintln("Provisioning application.")
	appMsg, err := RunAppRun([]string{templateId})
	CheckTestError(err, t)
	appId := strings.Split(appMsg, " ")[2]

	testPrintln("Restarting the application.")
	appMsg, err = RunAppRestart([]string{appId})
	CheckTestError(err, t)

	testPrintln("Removing the application.")
	appMsg, err = RunAppRemove([]string{appId})
	CheckTestError(err, t)

	// Clean up env
	testPrintln("Removing the host.")
	hostRemoveCmd.ParseFlags([]string{"--force"})
	hostMsg, err = RunHostRemove([]string{hostId})
	CheckTestError(err, t)

	testPrintln("Removing the template.")
	templateMsg, err = RunTemplateRemove([]string{templateId})
	CheckTestError(err, t)
}
