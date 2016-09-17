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

	"admiral/deplPolicy"

	"github.com/spf13/cobra"
)

var (
	dpName        string
	dpDescription string
)

func init() {
	dpAddCmd.Flags().StringVar(&dpDescription, "description", "", "(Required) Deployment policy description.")
	DeploymentPoliciesRootCmd.AddCommand(dpAddCmd)
}

var dpAddCmd = &cobra.Command{
	Use:   "add [NAME]",
	Short: "Adds deployment policy.",
	Long:  "Adds deployment policy.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			id     string
			err    error
			dpName string
			ok     bool
		)
		if dpName, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter deployment policy name.")
			return
		}
		id, err = deplPolicy.AddDP(dpName, dpDescription)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Deployment policy added: " + id)
		}
	},
}
