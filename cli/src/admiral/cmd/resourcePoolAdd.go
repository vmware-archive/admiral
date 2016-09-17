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

	"admiral/resourcePools"

	"github.com/spf13/cobra"
)

func init() {
	//Flag for custom properties.
	rpAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	ResourcePoolsRootCmd.AddCommand(rpAddCmd)
}

var rpAddCmd = &cobra.Command{
	Use: "add [NAME]",

	Short: "Add resource pool by given name.",

	Long: "Add resource pool by given name.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			rpName string
			ok     bool
		)
		if rpName, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter resource pool name.")
			return
		}
		id, err := resourcePools.AddRP(rpName, custProps)
		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Resource pool added: " + id)
		}
	},
}
