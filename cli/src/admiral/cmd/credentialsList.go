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

	"admiral/credentials"
	"admiral/help"

	"github.com/spf13/cobra"
)

func init() {
	listCredCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	CredentialsRootCmd.AddCommand(listCredCmd)
}

var listCredCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists credentials.",
	Long:  "Lists credentials.",

	//Main function for the "ls-cred" command.
	//It doesn't require any arguments.
	Run: func(cmd *cobra.Command, args []string) {
		lc := &credentials.ListCredentials{}
		count := lc.FetchCredentials()
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		lc.Print()
	},
}
