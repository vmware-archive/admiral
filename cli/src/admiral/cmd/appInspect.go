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

	"admiral/apps"

	"github.com/spf13/cobra"
)

func init() {
	AppsRootCmd.AddCommand(appInspectCmd)
}

var appInspectCmd = &cobra.Command{
	Use:   "inspect [APPLICATION-ID]",
	Short: "Inspect application for additional info.",
	Long:  "Inspect application for additional info.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			id string
			ok bool
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter application ID.")
			return
		}
		apps.InspectID(id)
	},
}
