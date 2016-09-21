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

	"errors"

	"github.com/spf13/cobra"
)

var resourcePoolIdError = errors.New("Resource pool ID not provided.")

func init() {
	initResourcePoolAdd()
	initResourcePoolList()
	initResourcePoolRemove()
	initResourcePoolUpdate()
}

var resourcePoolAddCmd = &cobra.Command{
	Use: "add [NAME]",

	Short: "Add resource pool by given name.",

	Long: "Add resource pool by given name.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunResourcePoolAdd(args)
		processOutput(output, err)
	},
}

func initResourcePoolAdd() {
	resourcePoolAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	ResourcePoolsRootCmd.AddCommand(resourcePoolAddCmd)
}

func RunResourcePoolAdd(args []string) (string, error) {
	var (
		rpName string
		ok     bool
	)
	if rpName, ok = ValidateArgsCount(args); !ok {
		return "", errors.New("Resource pool name not provided.")
	}
	id, err := resourcePools.AddRP(rpName, custProps)
	if err != nil {
		return "", err
	} else {
		return "Resource pool added: " + id, err
	}
}

var resourcePoolListCmd = &cobra.Command{
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

func initResourcePoolList() {
	resourcePoolListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	ResourcePoolsRootCmd.AddCommand(resourcePoolListCmd)
}

var resourcePoolRemoveCmd = &cobra.Command{
	Use: "rm [RESOURCE-POOL-ID]",

	Short: "Removes existing resource pool.",

	Long: "Removes existing resource pool",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunResourcePoolRemove(args)
		processOutput(output, err)
	},
}

func initResourcePoolRemove() {
	ResourcePoolsRootCmd.AddCommand(resourcePoolRemoveCmd)
}

func RunResourcePoolRemove(args []string) (string, error) {
	var (
		err   error
		newID string
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", resourcePoolIdError
	}
	newID, err = resourcePools.RemoveRPID(id)

	if err != nil {
		return "", err
	} else {
		return "Resource pool removed: " + newID, err
	}
}

var resourcePoolUpdateCmd = &cobra.Command{
	Use:   "update [RESOURCE-POOL-ID]",
	Short: "Edit resource pool",
	Long:  "Edit resource pool",
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunResourcePoolUpdate(args)
		processOutput(output, err)
	},
}

func initResourcePoolUpdate() {
	resourcePoolUpdateCmd.Flags().StringVar(&newName, "name", "", "New name of resource pool.")
	ResourcePoolsRootCmd.AddCommand(resourcePoolUpdateCmd)
}

func RunResourcePoolUpdate(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", resourcePoolIdError
	}
	newID, err = resourcePools.EditRPID(id, newName)

	if err != nil {
		return "", err
	} else {
		return "Resource pool updated: " + newID, err
	}
}
