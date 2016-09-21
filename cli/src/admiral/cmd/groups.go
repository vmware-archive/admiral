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
	"errors"

	"admiral/groups"

	"github.com/spf13/cobra"
)

var groupIdError = errors.New("Group ID not provided.")

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
		output, err := RunGroupAdd(args)
		processOutput(output, err)
	},
}

func initGroupAdd() {
	groupAddCmd.Flags().StringVar(&groupDescription, "description", "", "Group description.")
	GroupsRootCmd.AddCommand(groupAddCmd)
}

func RunGroupAdd(args []string) (string, error) {
	var (
		newID string
		err   error
		name  string
		ok    bool
	)
	if name, ok = ValidateArgsCount(args); !ok {
		return "", errors.New("Group name not provided.")
	}
	newID, err = groups.AddGroup(name, groupDescription)

	if err != nil {
		return "", err
	} else {
		return "Group added: " + newID, err
	}
}

var groupListCmd = &cobra.Command{
	Use:   "ls",
	Short: "List groups.",
	Long:  "List groups.",

	Run: func(cmd *cobra.Command, args []string) {
		RunGroupList(args)
	},
}

func initGroupList() {
	GroupsRootCmd.AddCommand(groupListCmd)
}

func RunGroupList(args []string) {
	gl := &groups.GroupList{}
	gl.FetchGroups()
	gl.Print()
}

var groupRemoveCmd = &cobra.Command{
	Use:   "rm [GROUP-ID]",
	Short: "Remove group.",
	Long:  "Remove group.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunGroupRemove(args)
		processOutput(output, err)
	},
}

func initGroupRemove() {
	GroupsRootCmd.AddCommand(groupRemoveCmd)
}

func RunGroupRemove(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", groupIdError
	}
	newID, err = groups.RemoveGroupID(id)

	if err != nil {
		return "", err
	} else {
		return "Group removed: " + newID, err
	}
}

var groupUpdateCmd = &cobra.Command{
	Use:   "update [GROUP-ID]",
	Short: "Update group.",
	Long:  "Update group.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunGroupUpdate(args)
		processOutput(output, err)
	},
}

func initGroupUpdate() {
	groupUpdateCmd.Flags().StringVar(&newName, "name", "", "New name.")
	groupUpdateCmd.Flags().StringVar(&newDescription, "description", "", "New description.")
	GroupsRootCmd.AddCommand(groupUpdateCmd)
}

func RunGroupUpdate(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", groupIdError
	}
	newID, err = groups.EditGroupID(id, newName, newDescription)

	if err != nil {
		return "", err
	} else {
		return "Group updated: " + newID, err
	}
}
