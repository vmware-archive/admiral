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

	"admiral/registries"

	"github.com/spf13/cobra"
)

func init() {
	RegistriesRootCmd.AddCommand(regDisCmd)
	RegistriesRootCmd.AddCommand(regEnCmd)
}

var regDisCmd = &cobra.Command{
	Use:   "disable [REGISTRY-ID]",
	Short: "Disable registry.",
	Long:  "Disable registry.",
	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter registry ID.")
			return
		}
		newID, err = registries.DisableID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Registry disabled: " + newID)
		}
	},
}

var regEnCmd = &cobra.Command{
	Use:   "enable [REGISTRY-ID]",
	Short: "Enable registry.",
	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter registry ID.")
			return
		}
		newID, err = registries.EnableID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Registry enabled: " + newID)
		}
	},
}
