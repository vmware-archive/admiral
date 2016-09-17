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

var (
	newAddress string
	newCred    string
)

func init() {
	updateRegistryCmd.Flags().StringVar(&newAddress, "address", "", "New address of registry.")
	updateRegistryCmd.Flags().StringVar(&newCred, "credentials", "", "New credentials name.")
	updateRegistryCmd.Flags().StringVar(&newName, "name", "", "New registry name.")
	updateRegistryCmd.Flags().BoolVar(&autoAccept, "accept", false, "Auto accept if certificate is not trusted.")
	RegistriesRootCmd.AddCommand(updateRegistryCmd)
}

var updateRegistryCmd = &cobra.Command{
	Use:   "update [REGISTRY-ID]",
	Short: "",
	Long:  "",

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
		newID, err = registries.EditRegistryID(id, newAddress, newName, newCred, autoAccept)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Registry updated: " + newID)
		}
	},
}
