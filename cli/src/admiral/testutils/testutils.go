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

package testutils

import (
	"encoding/json"
	"fmt"
	"os"
	"testing"

	"log"

	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
)

type TestConfig struct {
	AdmiralAddress string `json:"admiralAddress"`
	Username       string `json:"username"`
	Password       string `json:"password"`
	PublicKey      string `json:"publicKey"`
	PrivateKey     string `json:"privateKey"`
	HostAddress    string `json:"hostAddress"`
	PlacementZone  string `json:"placementZone"`
}

func ConfigureTestEnv() (*TestConfig, error) {
	file, err := os.Open("../testdata/test.config")
	if err != nil {
		return nil, err
	}
	defer file.Close()
	tc := &TestConfig{}
	decoder := json.NewDecoder(file)
	err = decoder.Decode(tc)
	return tc, err
}

func CheckTestError(err error, t *testing.T) {
	if err != nil {
		log.Println(err)
		t.Error(err)
	}
}

func CheckTestErrors(errs []error, t *testing.T) {
	for _, err := range errs {
		CheckTestError(err, t)
	}
}

func TestPrintln(s string) {
	output := "\x1b[31;1m----->" + s + "\x1b[37;1m"
	fmt.Println(output)
}

func ResetFlagValues(c *cobra.Command) {
	c.Flags().VisitAll(func(f *pflag.Flag) {
		f.Value.Set(f.DefValue)
	})
}
