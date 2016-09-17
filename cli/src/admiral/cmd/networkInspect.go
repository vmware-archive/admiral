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

	"admiral/network"

	"github.com/spf13/cobra"
)

func init() {
	NetworksRootCmd.AddCommand(networkInspCmd)
}

var networkInspCmd = &cobra.Command{
	Use:   "inspect [NETWORK-NAME]",
	Short: "Display detailed network information",
	Long:  "Display detailed network information",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			name string
			ok   bool
		)
		if name, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter network.")
			return
		}
		found, str := network.InspectNetwork(name)
		if !found {
			fmt.Println("Network not found.")
			return
		}
		fmt.Println(str)
	},
}
