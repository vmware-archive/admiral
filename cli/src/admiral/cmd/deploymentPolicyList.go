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

	"admiral/deplPolicy"
	"admiral/help"

	"github.com/spf13/cobra"
)

func init() {
	dpListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	DeploymentPoliciesRootCmd.AddCommand(dpListCmd)
}

var dpListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing deployment policies.",
	Long:  "Lists existing deployment policies.",

	Run: func(cmd *cobra.Command, args []string) {
		dpl := &deplPolicy.DeploymentPolicyList{}
		count := dpl.FetchDP()
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		dpl.Print()
	},
}
