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
	"errors"
	"strings"

	"admiral/config"

	"github.com/spf13/cobra"
)

var (
	KeyNotProvidedError   = errors.New("Key not provided.")
	ValueNotProvidedError = errors.New("Value not provided.")
	InvalidKeyError       = errors.New("Invalid key.")
)

func init() {
	cfgGetCmd.Flags().StringVarP(&keyProp, "key", "k", "", required+keyPropDesc)

	cfgSetCmd.Flags().StringVarP(&keyProp, "key", "k", "", required+keyPropDesc)
	cfgSetCmd.Flags().StringVarP(&valProp, "value", "v", "", required+valPropDesc)

	ConfigRootCmd.AddCommand(cfgGetCmd)
	ConfigRootCmd.AddCommand(cfgSetCmd)
	ConfigRootCmd.AddCommand(cfgInspectCmd)
}

var cfgGetCmd = &cobra.Command{
	Use:   "get",
	Short: "Set value from provided key key.",
	Long:  "Set value from provided key key.",
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunCfgGet(args)
		processOutput(output, err)
	},
}

func RunCfgGet(args []string) (string, error) {
	if keyProp == "" {
		return "", KeyNotProvidedError
	}

	v := config.GetProperty(strings.Title(keyProp))

	if v.IsValid() {
		return v.String(), nil
	} else {
		return "", InvalidKeyError
	}
}

var cfgSetCmd = &cobra.Command{
	Use:   "set",
	Short: "Set value to provided key.",
	Long:  "Set value to provided key.",
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunCfgSet(args)
		processOutput(output, err)
	},
}

func RunCfgSet(args []string) (string, error) {
	if keyProp == "" {
		return "", KeyNotProvidedError
	}
	if valProp == "" {
		return "", ValueNotProvidedError
	}

	isSet := config.SetProperty(strings.Title(keyProp), valProp)
	if isSet {
		return "New property set successfully.", nil
	} else {
		return "", errors.New("Error when setting new property.")
	}
}

var cfgInspectCmd = &cobra.Command{
	Use:   "inspect",
	Short: "Shows current config properties",
	Long:  "Shows current config properties",
	Run: func(cmd *cobra.Command, args []string) {
		output := RunCfgInspect(args)
		processOutput(output, nil)
	},
}

func RunCfgInspect(args []string) string {
	jsonBody := config.Inspect()
	return string(jsonBody)
}
