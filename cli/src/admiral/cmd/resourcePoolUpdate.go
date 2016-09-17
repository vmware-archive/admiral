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

	"admiral/resourcePools"

	"github.com/spf13/cobra"
)

func init() {
	rpUpdateCmd.Flags().StringVar(&newName, "name", "", "New name of resource pool.")
	ResourcePoolsRootCmd.AddCommand(rpUpdateCmd)
}

var rpUpdateCmd = &cobra.Command{
	Use:   "update [RESOURCE-POOL-ID]",
	Short: "Edit resource pool",
	Long:  "Edit resource pool",
	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter resource pool.")
			return
		}
		newID, err = resourcePools.EditRPID(id, newName)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Resource pool updated: " + newID)
		}
	},
}
