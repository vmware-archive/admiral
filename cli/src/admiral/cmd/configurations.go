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
	keyProp string
	valProp string
)

func init() {
	cfgGetCmd.Flags().StringVarP(&keyProp, "key", "k", "", "(Required) Key")

	cfgSetCmd.Flags().StringVarP(&keyProp, "key", "k", "", "(Required) Key")
	cfgSetCmd.Flags().StringVarP(&valProp, "value", "v", "", "(Required) Value")

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
		return "", errors.New("Key not provided.")
	}

	v := config.GetProperty(strings.Title(keyProp))

	if v.IsValid() {
		return v.String(), nil
	} else {
		return "", errors.New("Invalid key.")
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
		return "", errors.New("Key not provided.")
	}
	if valProp == "" {
		return "", errors.New("Value not provided.")
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
