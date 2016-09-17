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
	exportAppCmd.Flags().StringVar(&formatTemplate, "format", "yaml", "(Required) File format - yaml/docker")
	exportAppCmd.Flags().StringVar(&dirF, "file", "", "(Required) path/to/file")
	TemplatesRootCmd.AddCommand(exportAppCmd)
}

//Function to verify the given template in the flag.
//Returns true if format is valid, false if invalid.
func verifyFormat() bool {
	if formatTemplate != "yaml" && formatTemplate != "docker" {
		fmt.Println("Choose either yaml or docker file format.")
		return false
	}
	return true
}

var exportAppCmd = &cobra.Command{
	Use:   "export [TEMPLATE-ID]",
	Short: "Download exported application.",
	Long:  "Download exported application.",

	//Main function for the command "export". Args are joined with space and this is the application name.
	//For now exports the the first with that name.
	Run: func(cmd *cobra.Command, args []string) {
		if !verifyFormat() {
			return
		}
		var (
			id string
			ok bool
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter template ID.")
			return
		}
		newID, err := apps.Export(id, dirF, formatTemplate)
		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Template exported: " + newID)
		}
	},
}
