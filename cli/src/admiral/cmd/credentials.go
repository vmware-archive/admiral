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

	"admiral/credentials"
	"admiral/help"

	"github.com/spf13/cobra"
)

var MissingCredentialsIdError = errors.New("Credentials ID not provided.")

func init() {
	initCredentialsAdd()
	initCredentialsList()
	initCredentialsRemove()
	initCredentialsUpdate()
}

var credentialsAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Add credentials",
	Long:  "Add credentials",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunCredentialsAdd(args)
		processOutput(output, err)
	},
}

func initCredentialsAdd() {
	credentialsAddCmd.Flags().StringVar(&credName, "name", "", "(Required) Credentials name.")
	credentialsAddCmd.Flags().StringVar(&publicCert, "public", "", "(Required if using certificates)"+publicCertDesc)
	credentialsAddCmd.Flags().StringVar(&privateCert, "private", "", "(Required if using ceritficates)"+privateCertDesc)
	credentialsAddCmd.Flags().StringVar(&userName, "username", "", "(Required if using username) Username.")
	credentialsAddCmd.Flags().StringVar(&passWord, "password", "", "(Required if using username) Password.")
	credentialsAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	CredentialsRootCmd.AddCommand(credentialsAddCmd)
}

func RunCredentialsAdd(args []string) (string, error) {
	if credName == "" {
		return "", errors.New("Provide crendetial name.")
	}
	var (
		newID string
		err   error
	)
	if userName != "" && passWord != "" {
		newID, err = credentials.AddByUsername(credName, userName, passWord, custProps)
	} else if publicCert != "" && privateCert != "" {
		newID, err = credentials.AddByCert(credName, publicCert, privateCert, custProps)
	} else {
		return "", errors.New("Missing required flags.")
	}

	if err != nil {
		return "", err
	} else {
		return "Credentials added: " + newID, err
	}

}

var credentialsListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists credentials.",
	Long:  "Lists credentials.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunCredentialsList(args)
		formatAndPrintOutput(output, err)
	},
}

func initCredentialsList() {
	credentialsListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	CredentialsRootCmd.AddCommand(credentialsListCmd)
}

func RunCredentialsList(args []string) (string, error) {
	lc := &credentials.ListCredentials{}
	_, err := lc.FetchCredentials()
	if err != nil {
		return "", err
	}
	return lc.GetOutputString(), nil
}

var credentialsRemoveCmd = &cobra.Command{
	Use:   "rm [CREDENTIALS-ID]",
	Short: "Removes existing credentials.",
	Long:  "Removes existing credentials.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunCredentialsRemove(args)
		processOutput(output, err)
	},
}

func initCredentialsRemove() {
	CredentialsRootCmd.AddCommand(credentialsRemoveCmd)
}

func RunCredentialsRemove(args []string) (string, error) {
	var (
		newID string
		err   error
		ok    bool
		id    string
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingCredentialsIdError
	}
	newID, err = credentials.RemoveCredentialsID(id)

	if err != nil {
		return "", err
	} else {
		return "Credentials removed: " + newID, err
	}
}

var credentialsUpdateCmd = &cobra.Command{
	Use:   "update [CREDENTIALS-ID]",
	Short: "Update credentials.",
	Long:  "Update credentials.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunCredentialsUpdate(args)
		processOutput(output, err)
	},
}

func initCredentialsUpdate() {
	credentialsUpdateCmd.Flags().StringVar(&publicCert, "public", "", "Location to new public key.")
	credentialsUpdateCmd.Flags().StringVar(&privateCert, "private", "", "Location to new private key.")
	credentialsUpdateCmd.Flags().StringVar(&userName, "username", "", "New username.")
	credentialsUpdateCmd.Flags().StringVar(&passWord, "password", "", "New password.")
	CredentialsRootCmd.AddCommand(credentialsUpdateCmd)
}

func RunCredentialsUpdate(args []string) (string, error) {
	var (
		newID string
		err   error
		ok    bool
		id    string
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingCredentialsIdError
	}
	id, err = credentials.EditCredetialsID(id, publicCert, privateCert, userName, passWord)

	if err != nil {
		return "", err
	} else {
		return "Credentials updated: " + newID, err
	}
}
