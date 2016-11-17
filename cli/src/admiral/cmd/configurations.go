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

	initCfgEncrypt()
	initCfgDecrypt()

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

const ENCRYPT_PREFIX = "s2enc~"

var cfgEncryptCmd = &cobra.Command{
	Use:   "encrypt [TEXT]",
	Short: "Encrypt text.",
	Long:  "Encrypt text.",
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunCfgEncrypt(args)
		processOutput(output, err)
	},
}

func initCfgEncrypt() {
	cfgEncryptCmd.Flags().StringVar(&encryptionKey, "encryption-key", "", "File containing encryption key.")
	ConfigRootCmd.AddCommand(cfgEncryptCmd)
}

func RunCfgEncrypt(args []string) (string, error) {
	toEncrypt := strings.Join(args, " ")
	if strings.HasPrefix(toEncrypt, ENCRYPT_PREFIX) {
		return "", errors.New("The input is already encrypted.")
	}
	data, err := config.Encrypt(toEncrypt, encryptionKey)
	return ENCRYPT_PREFIX + string(data), err
}

var cfgDecryptCmd = &cobra.Command{
	Use:   "decrypt [TEXT]",
	Short: "Decrypt text.",
	Long:  "Decrypt text.",
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunCfgDecrypt(args)
		processOutput(output, err)
	},
}

func initCfgDecrypt() {
	cfgDecryptCmd.Flags().StringVar(&encryptionKey, "encryption-key", "", "File containing encryption key.")
	ConfigRootCmd.AddCommand(cfgDecryptCmd)
}

func RunCfgDecrypt(args []string) (string, error) {
	toDecrypt := strings.Join(args, " ")
	if strings.HasPrefix(toDecrypt, ENCRYPT_PREFIX) {
		toDecrypt = strings.Replace(toDecrypt, ENCRYPT_PREFIX, "", 1)
	}
	data, err := config.Decrypt(toDecrypt, encryptionKey)
	return string(data), err
}
