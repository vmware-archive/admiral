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
	"admiral/groups"
	"fmt"

	"github.com/spf13/cobra"
)

func init() {
	initGroupAdd()
	initGroupList()
	initGroupRemove()
	initGroupUpdate()
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

func initGroupAdd() {
	groupAddCmd.Flags().StringVar(&groupDescription, "description", "", "Group description.")
	GroupsRootCmd.AddCommand(groupAddCmd)
}

var groupListCmd = &cobra.Command{
	Use:   "ls",
	Short: "List groups.",
	Long:  "List groups.",

	Run: func(cmd *cobra.Command, args []string) {
		gl := &groups.GroupList{}
		gl.FetchGroups()
		gl.Print()
	},
}

func initGroupList() {
	GroupsRootCmd.AddCommand(groupListCmd)
}

var groupRemoveCmd = &cobra.Command{
	Use:   "rm [GROUP-ID]",
	Short: "Remove group.",
	Long:  "Remove group.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter group ID.")
			return
		}
		newID, err = groups.RemoveGroupID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Group removed: " + newID)
		}

	},
}

func initGroupRemove() {
	GroupsRootCmd.AddCommand(groupRemoveCmd)
}

var groupUpdateCmd = &cobra.Command{
	Use:   "update [GROUP-ID]",
	Short: "Update group.",
	Long:  "Update group.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter group ID.")
			return
		}
		newID, err = groups.EditGroupID(id, newName, newDescription)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Group updated: " + newID)
		}
	},
}

func initGroupUpdate() {
	groupUpdateCmd.Flags().StringVar(&newName, "name", "", "New name.")
	groupUpdateCmd.Flags().StringVar(&newDescription, "description", "", "New description.")
	GroupsRootCmd.AddCommand(groupUpdateCmd)
}
