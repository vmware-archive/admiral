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

var keepTemplate bool

func init() {
	appRunCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	appRunCmd.Flags().StringVar(&dirF, "file", "", "Provision template from file.")
	appRunCmd.Flags().BoolVar(&keepTemplate, "keep", false, "Do not remove template after provisioning.")
	appRunCmd.Flags().StringVar(&groupID, "group", "", "(Required) "+groupIDDesc)
	AppsRootCmd.AddCommand(appRunCmd)
}

var appRunCmd = &cobra.Command{
	Use:   "run [TEMPLATE-ID]",
	Short: "Provision application from template.",
	Long:  "Provision application from template.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			IDs []string
			err error
			ok  bool
			id  string
		)

		if dirF != "" {
			IDs, err = apps.RunAppFile(dirF, keepTemplate, asyncTask)
		} else {
			if id, ok = ValidateArgsCount(args); !ok {
				fmt.Println("Enter template ID.")
				return
			}
			IDs, err = apps.RunAppID(id, asyncTask)
		}

		if err != nil {
			fmt.Println(err)
		} else if len(IDs) > 0 {
			if asyncTask {
				fmt.Println("Application is provisioning: " + IDs[0])
			} else {
				fmt.Println("Application provisioned: " + IDs[0])
			}
		}
	},
}
