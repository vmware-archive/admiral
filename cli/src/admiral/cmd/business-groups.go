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
	"admiral/businessgroups"

	"github.com/spf13/cobra"
)

func init() {
	initBusinessGroupsList()
}

var businessGroupListCmd = &cobra.Command{
	Use:   "ls",
	Short: "List business groups.",
	Long:  "List business groups.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunBusinessGroupList(args)
		formatAndPrintOutput(output, err)
	},
}

func initBusinessGroupsList() {
	BusinessGroupsRootCmd.AddCommand(businessGroupListCmd)
}

func RunBusinessGroupList(args []string) (string, error) {
	bgl := &businessgroups.BusinessGroupList{}
	_, err := bgl.FetchBusinessGroups()
	if err != nil {
		return "", err
	}
	return bgl.GetOutputString(), nil
}
