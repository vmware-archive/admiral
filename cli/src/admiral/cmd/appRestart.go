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
	resAppCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	AppsRootCmd.AddCommand(resAppCmd)
}

var resAppCmd = &cobra.Command{
	Use:   "restart [APPLICATION-ID]",
	Short: "Restarts application.",
	Long:  "Restarts application.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			IDs []string
			err error
			ok  bool
			id  string
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter application ID.")
			return
		}
		IDs, err = apps.StopAppID(id, asyncTask)
		IDs, err = apps.StartAppID(id, asyncTask)

		if err != nil {
			fmt.Println(err)
		} else if len(IDs) > 0 {
			if asyncTask {
				fmt.Println("Application is being restarted: " + IDs[0])
			} else {
				fmt.Println("Application restarted: " + IDs[0])
			}
		}
	},
}
