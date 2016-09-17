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
	updateHostCmd.Flags().StringVar(&hostName, "name", "", "New host name.")
	updateHostCmd.Flags().StringVar(&credName, "credentials", "", "New credentials ID.")
	updateHostCmd.Flags().StringVar(&resPoolF, "resource-pool", "", "New resource pool ID.")
	updateHostCmd.Flags().StringVar(&deplPolicyF, "deployment-policy", "", "New deployment policy ID.")
	updateHostCmd.Flags().BoolVar(&autoAccept, "accept", false, "Auto accept if certificate is not trusted.")
	HostsRootCmd.AddCommand(updateHostCmd)
}

var updateHostCmd = &cobra.Command{
	Use:   "update [ADDRESS]",
	Short: "Edit existing hosts.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			address string
			ok      bool
		)
		if address, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter host address.")
			return
		}
		newID, err := hosts.EditHost(address, hostName, resPoolF, deplPolicyF, credName, autoAccept)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Host updated: " + newID)
		}
	},
}
