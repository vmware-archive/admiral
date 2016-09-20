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
	"admiral/resourcePools"
	"fmt"

	"admiral/help"

	"github.com/spf13/cobra"
)

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
		var (
			rpName string
			ok     bool
		)
		if rpName, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter resource pool name.")
			return
		}
		id, err := resourcePools.AddRP(rpName, custProps)
		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Resource pool added: " + id)
		}
	},
}

func initResourcePoolAdd() {
	resourcePoolAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	ResourcePoolsRootCmd.AddCommand(resourcePoolAddCmd)
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
		var (
			err   error
			newID string
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter resource pool ID.")
			return
		}
		newID, err = resourcePools.RemoveRPID(id)

		if err == nil {
			fmt.Println("Resource pool removed: " + newID)
		} else if err != nil {
			fmt.Println(err)
		}
	},
}

func initResourcePoolRemove() {
	ResourcePoolsRootCmd.AddCommand(resourcePoolRemoveCmd)
}

var resourcePoolUpdateCmd = &cobra.Command{
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

func initResourcePoolUpdate() {
	resourcePoolUpdateCmd.Flags().StringVar(&newName, "name", "", "New name of resource pool.")
	ResourcePoolsRootCmd.AddCommand(resourcePoolUpdateCmd)
}
