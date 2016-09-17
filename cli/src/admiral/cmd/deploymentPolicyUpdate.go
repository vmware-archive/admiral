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

func init() {
	dpUpdateCmd.Flags().StringVar(&dpDescription, "description", "", "(Required) New deployment policy description.")
	dpUpdateCmd.Flags().StringVar(&dpName, "name", "", "(Required) New deployment policy name")
	DeploymentPoliciesRootCmd.AddCommand(dpUpdateCmd)
}

var dpUpdateCmd = &cobra.Command{
	Use:   "update [DEPLOYMENT-POLICY-ID]",
	Short: "Update deployment policy.",
	Long:  "Update deployment policy.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter deployment policy ID.")
			return
		}
		newID, err = deplPolicy.EditDPID(id, dpName, dpDescription)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Deployment policy updated: " + newID)
		}
	},
}
