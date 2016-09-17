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
	TemplatesRootCmd.AddCommand(importAppCmd)
}

var importAppCmd = &cobra.Command{
	Use:   "import [PATH/TO/FILE]",
	Short: "Import yaml file.",
	Long:  "Import yaml file.",

	//Main function for the command "import". No args are needed, just path to file after -f or --file flag.
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) < 1 {
			fmt.Println("Please enter existing file.")
			return
		}
		var (
			filePath string
			ok       bool
		)
		if filePath, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter path to file.")
			return
		}
		id, err := apps.Import(filePath)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Template imported: " + id)
		}
	},
}
