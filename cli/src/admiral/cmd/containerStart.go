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
	"strings"

	"admiral/containers"

	"github.com/spf13/cobra"
)

func init() {
	startContainerCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(startContainerCmd)
}

//Main function to start existing container by provided name.
//At this state it will try to start all of the given containers, without check their current state or if non-unique names are given.
var startContainerCmd = &cobra.Command{
	Use:   "start [CONTAINER-ID]...",
	Short: "Starts existing container",
	Long:  "Starts existing container",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			resIDs []string
			err    error
		)
		if len(args) > 0 {
			resIDs, err = containers.StartContainer(args, asyncTask)
		} else {
			fmt.Println("Enter container(s) ID.")
			return
		}

		if err != nil {
			fmt.Println(err)
		} else {
			if asyncTask {
				fmt.Println("Container(s) are being started: " + strings.Join(resIDs, " "))
			} else {
				fmt.Println("Container(s) started: " + strings.Join(resIDs, " "))
			}
		}
	},
}
