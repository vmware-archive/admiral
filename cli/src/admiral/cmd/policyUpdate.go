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

	"admiral/policies"

	"github.com/spf13/cobra"
)

var (
	priorityInt        int32
	maxNumberInstances int32
	cpuSharesInt       int32
)

func init() {
	updatePolCmd.Flags().StringVar(&newName, "name", "", "New name")
	updatePolCmd.Flags().Int32Var(&cpuSharesInt, "cpu", -1, "New CPU shares.")
	updatePolCmd.Flags().Int32Var(&maxNumberInstances, "instances", -1, "New instances")
	updatePolCmd.Flags().Int32Var(&priorityInt, "prio", -1, "New priority")
	updatePolCmd.Flags().StringVar(&tenants, "group", "", "New group")
	updatePolCmd.Flags().StringVar(&resPoolID, "resource-pool", "", "New resource pool ID")
	updatePolCmd.Flags().StringVar(&deplPolID, "deployment-policy", "", "New deployment policy ID")
	updatePolCmd.Flags().StringVar(&memoryLimitStr, "memory", "0kb", "New memory limit. Default unit: kb. Units supported: kb/mb/gb. Example: 1024mb")
	PoliciesRootCmd.AddCommand(updatePolCmd)
}

var updatePolCmd = &cobra.Command{
	Use:   "update [POLICY-ID]",
	Short: "Update policy.",
	Long:  "Update policy.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)
		memoryLimit, err := parseMemory(memoryLimitStr)
		if err != nil {
			fmt.Println(err)
			return
		}

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter policy ID.")
			return
		}
		newID, err = policies.EditPolicyID(id, newName, tenants, resPoolID, deplPolID, cpuSharesInt, maxNumberInstances, priorityInt, memoryLimit)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Policy updates: " + newID)
		}

	},
}
