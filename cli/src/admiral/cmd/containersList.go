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

	"admiral/containers"
	"admiral/help"

	"github.com/spf13/cobra"
)

var allContainers bool

func init() {
	listContainerCmd.Flags().BoolVarP(&allContainers, "all", "a", false, "Show all containers.")
	listContainerCmd.Flags().StringVarP(&queryF, "query", "q", "", "Add query.")
	listContainerCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	RootCmd.AddCommand(listContainerCmd)
}

var listContainerCmd = &cobra.Command{
	Use:   "ps",
	Short: "Lists existing containers.",
	Long:  "Lists existing containers.",

	//Main function for "ls" command. It doesn't require any arguments.
	Run: func(cmd *cobra.Command, args []string) {
		lc := &containers.ListContainers{}
		count := lc.FetchContainers(queryF)
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		lc.Print(allContainers)
	},
}
