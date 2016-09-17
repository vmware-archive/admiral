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

	"admiral/groups"

	"github.com/spf13/cobra"
)

var groupDescription string

func init() {
	groupAddCmd.Flags().StringVar(&groupDescription, "description", "", "Group description.")
	GroupsRootCmd.AddCommand(groupAddCmd)
}

var groupAddCmd = &cobra.Command{
	Use:   "add [NAME]",
	Short: "Add group.",
	Long:  "Add group.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			name  string
			ok    bool
		)
		if name, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter group name.")
			return
		}
		newID, err = groups.AddGroup(name, groupDescription)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Print("Group added: " + newID)
		}
	},
}
