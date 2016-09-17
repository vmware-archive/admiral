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

	"admiral/help"
	"admiral/resourcePools"

	"github.com/spf13/cobra"
)

func init() {
	rpListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	ResourcePoolsRootCmd.AddCommand(rpListCmd)
}

var rpListCmd = &cobra.Command{
	Use: "ls",

	Short: "Lists existing resource pools.",

	Long: "Lists existing resource pools.",

	Run: func(cmd *cobra.Command, args []string) {
		rpl := resourcePools.ResourcePoolList{}
		count := rpl.FetchRP()
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		rpl.Print()
	},
}
