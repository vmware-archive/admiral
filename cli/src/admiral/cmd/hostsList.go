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
	"admiral/hosts"

	"github.com/spf13/cobra"
)

func init() {
	listHostsCmd.Flags().StringVarP(&queryF, "query", "q", "", "Add query.")
	listHostsCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	HostsRootCmd.AddCommand(listHostsCmd)
}

var listHostsCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing hosts.",
	Long:  "Lists existing hosts.",

	//Main function for the "ls-host" command.
	//It doesn't require any args, but there is optional -q or --query flag,
	//after which you can provide specific keyword to look for.
	Run: func(cmd *cobra.Command, args []string) {
		hl := &hosts.HostsList{}
		count := hl.FetchHosts(queryF)
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		hl.Print()
	},
}
