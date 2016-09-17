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

	"admiral/functions"
	"admiral/hosts"

	"github.com/spf13/cobra"
)

func init() {
	removeHostCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	HostsRootCmd.AddCommand(removeHostCmd)
}

var removeHostCmd = &cobra.Command{
	Use: "rm [HOST-ADDRESS]",

	Short: "Remove existing host.",

	Long: "Remove existing host.",

	//Main function for the "rm-host" command.
	//If any of the provided host doesn't exist, the command will be aborted.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			address string
			ok      bool
		)
		if address, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter host address.")
			return
		}
		fmt.Printf("Are you sure you want to remove %s? (y/n)\n", address)
		answer := functions.PromptAgreement()
		if answer == "n" || answer == "no" {
			fmt.Println("Remove command aborted!")
			return
		}

		newID, err := hosts.RemoveHost(address, asyncTask)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Host removed: " + newID)
		}
	},
}
