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

	"github.com/spf13/cobra"
)

func init() {
	RootCmd.AddCommand(inspcContCmd)
}

var inspcContCmd = &cobra.Command{
	Use:   "inspect [CONTAINER-ID]",
	Short: "Return low-level information on a container.",
	Long:  "Return low-level information on a container.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			ok bool
			id string
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter container.")
			return
		}
		output, err := containers.InspectContainer(id)
		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println(output)
		}
	},
}
