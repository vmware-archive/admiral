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
	"admiral/registries"
	"fmt"

	"admiral/help"

	"github.com/spf13/cobra"
)

func init() {
	initRegistryAdd()
	initRegistryList()
	initRegistryEnableDisable()
	initRegistryRemove()
	initRegistryUpdate()
}

var registryAddCmd = &cobra.Command{
	Use:   "add [NAME]",
	Short: "Add registry",
	Long:  "Add registry",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			name string
			ok   bool
		)
		if name, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter policy name.")
			return
		}
		newID, err := registries.AddRegistry(name, addressF, credName, publicCert, privateCert, userName, passWord, autoAccept)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Registry added: " + newID)
		}
	},
}

var addressF string

func initRegistryAdd() {
	registryAddCmd.Flags().StringVar(&publicCert, "public", "", "(Required if adding new credentials)"+publicCertDesc)
	registryAddCmd.Flags().StringVar(&privateCert, "private", "", "(Required if adding new credentials)"+privateCertDesc)
	registryAddCmd.Flags().StringVar(&userName, "username", "", "(Required if adding new credentials)"+"Username.")
	registryAddCmd.Flags().StringVar(&passWord, "password", "", "(Required if adding new credentials)"+"Password.")
	registryAddCmd.Flags().StringVar(&addressF, "address", "", "(Required) Address of registry.")
	registryAddCmd.Flags().StringVar(&credName, "credentials", "", "(Required if using existing one.) Credentials ID.")
	registryAddCmd.Flags().BoolVar(&autoAccept, "accept", false, "Auto accept if certificate is not trusted.")
	registryAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	RegistriesRootCmd.AddCommand(registryAddCmd)
}

var registryListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing registries.",
	Long:  "Lists existing registries.",

	Run: func(cmd *cobra.Command, args []string) {
		rl := &registries.RegistryList{}
		rl.FetchRegistries()
		rl.Print()
	},
}

func initRegistryList() {
	registryListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	RegistriesRootCmd.AddCommand(registryListCmd)
}

var registryRemoveCmd = &cobra.Command{
	Use:   "rm [REGISTRY-ID]",
	Short: "Remove existing registry.",
	Long:  "Remove existing registry.",

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
		newID, err = registries.RemoveRegistryID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Registry removed: " + newID)
		}

	},
}

func initRegistryRemove() {
	RegistriesRootCmd.AddCommand(registryRemoveCmd)
}

var registryDisableCmd = &cobra.Command{
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

var registryEnableCmd = &cobra.Command{
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

func initRegistryEnableDisable() {
	RegistriesRootCmd.AddCommand(registryDisableCmd)
	RegistriesRootCmd.AddCommand(registryEnableCmd)
}

var registryUpdateCmd = &cobra.Command{
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

func initRegistryUpdate() {
	registryUpdateCmd.Flags().StringVar(&newAddress, "address", "", "New address of registry.")
	registryUpdateCmd.Flags().StringVar(&newCred, "credentials", "", "New credentials name.")
	registryUpdateCmd.Flags().StringVar(&newName, "name", "", "New registry name.")
	registryUpdateCmd.Flags().BoolVar(&autoAccept, "accept", false, "Auto accept if certificate is not trusted.")
	RegistriesRootCmd.AddCommand(registryUpdateCmd)
}
