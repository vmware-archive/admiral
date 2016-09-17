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

	"admiral/hosts"

	"github.com/spf13/cobra"
)

func init() {
	HostsRootCmd.AddCommand(enableHostCmd)
}

var enableHostCmd = &cobra.Command{
	Use:   "enable [HOST-ADDRESS]",
	Short: "Enable host with address provided.",
	Long:  "Enable host with address provided.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			hostAddress string
			ok          bool
		)
		if hostAddress, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter host address.")
			return
		}
		newID, err := hosts.EnableHost(hostAddress)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Host enabled: " + newID)
		}
	},
}
