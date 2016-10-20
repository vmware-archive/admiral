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
	"admiral/help"
	"admiral/registries"

	"errors"

	"github.com/spf13/cobra"
)

var (
	MissingRegistryIdError   = errors.New("Registry ID not provided.")
	MissingRegistryNameError = errors.New("Registry name not provided.")
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
		output, err := RunRegistryAdd(args)
		processOutput(output, err)
	},
}

var (
	addressF     string
	addressFDesc = "Address of registry."
)

func initRegistryAdd() {
	registryAddCmd.Flags().StringVar(&publicCert, "public", "", "*Required if adding new credentials* "+publicCertDesc)
	registryAddCmd.Flags().StringVar(&privateCert, "private", "", "*Required if adding new credentials* "+privateCertDesc)
	registryAddCmd.Flags().StringVar(&userName, "username", "", "*Required if adding new credentials* "+userNameDesc)
	registryAddCmd.Flags().StringVar(&passWord, "password", "", "*Required if adding new credentials* "+passWordDesc)
	registryAddCmd.Flags().StringVar(&credId, "credentials", "", "*Required if using existing credentials* "+credIdDesc)
	registryAddCmd.Flags().StringVar(&addressF, "address", "", required+addressFDesc)
	registryAddCmd.Flags().BoolVar(&autoAccept, "accept", false, autoAcceptDesc)
	registryAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	RegistriesRootCmd.AddCommand(registryAddCmd)
}

func RunRegistryAdd(args []string) (string, error) {
	var (
		name string
		ok   bool
	)
	if name, ok = ValidateArgsCount(args); !ok {
		return "", MissingRegistryNameError
	}
	newID, err := registries.AddRegistry(name, addressF, credId, publicCert, privateCert, userName, passWord, autoAccept)

	if err != nil {
		return "", err
	} else {
		return "Registry added: " + newID, err
	}
}

var registryListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing registries.",
	Long:  "Lists existing registries.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunRegistryList(args)
		formatAndPrintOutput(output, err)
	},
}

func initRegistryList() {
	registryListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	RegistriesRootCmd.AddCommand(registryListCmd)
}

func RunRegistryList(args []string) (string, error) {
	rl := &registries.RegistryList{}
	_, err := rl.FetchRegistries()
	return rl.GetOutputString(), err
}

var registryRemoveCmd = &cobra.Command{
	Use:   "rm [REGISTRY-ID]",
	Short: "Remove existing registry.",
	Long:  "Remove existing registry.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunRegistryRemove(args)
		processOutput(output, err)
	},
}

func initRegistryRemove() {
	RegistriesRootCmd.AddCommand(registryRemoveCmd)
}

func RunRegistryRemove(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingRegistryIdError
	}
	newID, err = registries.RemoveRegistryID(id)

	if err != nil {
		return "", err
	} else {
		return "Registry removed: " + newID, err
	}
}

var registryDisableCmd = &cobra.Command{
	Use:   "disable [REGISTRY-ID]",
	Short: "Disable registry.",
	Long:  "Disable registry.",
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunRegistryDisable(args)
		processOutput(output, err)
	},
}

func RunRegistryDisable(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingRegistryIdError
	}
	newID, err = registries.DisableID(id)

	if err != nil {
		return "", err
	} else {
		return "Registry disabled: " + newID, err
	}
}

var registryEnableCmd = &cobra.Command{
	Use:   "enable [REGISTRY-ID]",
	Short: "Enable registry.",
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunRegistryEnable(args)
		processOutput(output, err)
	},
}

func RunRegistryEnable(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingRegistryIdError
	}
	newID, err = registries.EnableID(id)

	if err != nil {
		return "", err
	} else {
		return "Registry enabled: " + newID, err
	}
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
		output, err := RunRegistryUpdate(args)
		processOutput(output, err)
	},
}

func initRegistryUpdate() {
	registryUpdateCmd.Flags().StringVar(&newAddress, "address", "", "New address of registry.")
	registryUpdateCmd.Flags().StringVar(&newCred, "credentials", "", "New credentials name.")
	registryUpdateCmd.Flags().StringVar(&newName, "name", "", "New registry name.")
	registryUpdateCmd.Flags().BoolVar(&autoAccept, "accept", false, autoAcceptDesc)
	RegistriesRootCmd.AddCommand(registryUpdateCmd)
}

func RunRegistryUpdate(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingRegistryIdError
	}
	newID, err = registries.EditRegistryID(id, newAddress, newName, newCred, autoAccept)

	if err != nil {
		return "", err
	} else {
		return "Registry updated: " + newID, err
	}
}
