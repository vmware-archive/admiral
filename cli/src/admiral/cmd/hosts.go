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
	"fmt"

	"admiral/help"
	"admiral/hosts"
	"admiral/utils"

	"github.com/spf13/cobra"
)

var MissingHostIdError = errors.New("Host ID not provided.")

func init() {
	initHostAdd()
	initHostDisable()
	initHostEnable()
	initHostRemove()
	initHostUpdate()
	initHostList()
}

var hostAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Add host",
	Long:  "Add host",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunAddHost(args)
		processOutput(output, err)
	},
}

func initHostAdd() {
	hostAddCmd.Flags().StringVar(&publicCert, "public", "", "(Required if adding new credentials)"+publicCertDesc)
	hostAddCmd.Flags().StringVar(&privateCert, "private", "", "(Required if adding new credentials)"+privateCertDesc)
	hostAddCmd.Flags().StringVar(&userName, "username", "", "(Required if adding new credentials)"+"Username.")
	hostAddCmd.Flags().StringVar(&passWord, "password", "", "(Required if adding new credentials)"+"Password.")
	hostAddCmd.Flags().StringVar(&ipF, "address", "", "(Required) Address of host.")
	hostAddCmd.Flags().StringVar(&placementZoneID, "placement-zone", "", "(Required) Placement zone ID.")
	hostAddCmd.Flags().StringVar(&credName, "credentials", "", "Credentials ID.")
	hostAddCmd.Flags().StringVar(&deplPolicyF, "deployment-policy", "", "Deployment policy ID.")
	hostAddCmd.Flags().BoolVar(&autoAccept, "accept", false, "Auto accept if certificate is not trusted.")
	hostAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	HostsRootCmd.AddCommand(hostAddCmd)
}

func RunAddHost(args []string) (string, error) {
	var (
		newID string
		err   error
	)
	newID, err = hosts.AddHost(ipF, placementZoneID, deplPolicyF, credName, publicCert, privateCert, userName, passWord,
		autoAccept,
		custProps)

	if err != nil {
		return "", err
	} else {
		return "Host added: " + newID, nil
	}
}

var hostDisableCmd = &cobra.Command{
	Use:   "disable [HOST-ID]",
	Short: "Disable host with address provided.",
	Long:  "Disable host with address provided.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunHostDisable(args)
		processOutput(output, err)
	},
}

func initHostDisable() {
	HostsRootCmd.AddCommand(hostDisableCmd)
}

func RunHostDisable(args []string) (string, error) {
	var (
		hostAddress string
		ok          bool
	)
	if hostAddress, ok = ValidateArgsCount(args); !ok {
		return "", MissingHostIdError
	}
	newID, err := hosts.DisableHost(hostAddress)

	if err != nil {
		return "", err
	} else {
		return "Host disabled " + newID, err
	}
}

var hostEnableCmd = &cobra.Command{
	Use:   "enable [HOST-ID]",
	Short: "Enable host with address provided.",
	Long:  "Enable host with address provided.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunHostEnable(args)
		processOutput(output, err)
	},
}

func initHostEnable() {
	HostsRootCmd.AddCommand(hostEnableCmd)
}

func RunHostEnable(args []string) (string, error) {
	var (
		hostAddress string
		ok          bool
	)
	if hostAddress, ok = ValidateArgsCount(args); !ok {
		return "", MissingHostIdError
	}
	newID, err := hosts.EnableHost(hostAddress)

	if err != nil {
		return "", err
	} else {
		return "Host enabled: " + newID, err
	}
}

var hostListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing hosts.",
	Long:  "Lists existing hosts.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunHostList(args)
		formatAndPrintOutput(output, err)
	},
}

func initHostList() {
	hostListCmd.Flags().StringVarP(&queryF, "query", "q", "", "Add query.")
	hostListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	HostsRootCmd.AddCommand(hostListCmd)
}

func RunHostList(args []string) (string, error) {
	hl := &hosts.HostsList{}
	_, err := hl.FetchHosts(queryF)
	return hl.GetOutputString(), err
}

var hostRemoveCmd = &cobra.Command{
	Use:   "rm [HOST-ID]",
	Short: "Remove existing host.",
	Long:  "Remove existing host.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunHostRemove(args)
		processOutput(output, err)
	},
}

func initHostRemove() {
	hostRemoveCmd.Flags().BoolVar(&forceF, "force", false, forceDesc)
	hostRemoveCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	HostsRootCmd.AddCommand(hostRemoveCmd)
}

func RunHostRemove(args []string) (string, error) {
	var (
		address string
		ok      bool
	)
	if address, ok = ValidateArgsCount(args); !ok {
		return "", MissingHostIdError
	}

	if !forceF {
		fmt.Printf("Are you sure you want to remove %s? (y/n)\n", address)
		answer := utils.PromptAgreement()
		if answer == "n" || answer == "no" {
			return "", errors.New("Remove command aborted!")
		}
	}

	newID, err := hosts.RemoveHost(address, asyncTask)

	if err != nil {
		return "", err
	} else {
		return "Host removed: " + newID, err
	}
}

var hostUpdateCmd = &cobra.Command{
	Use:   "update [HOST-ID]",
	Short: "Edit existing hosts.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunHostUpdate(args)
		processOutput(output, err)
	},
}

func initHostUpdate() {
	hostUpdateCmd.Flags().StringVar(&hostName, "name", "", "New host name.")
	hostUpdateCmd.Flags().StringVar(&credName, "credentials", "", "New credentials ID.")
	hostUpdateCmd.Flags().StringVar(&placementZoneID, "placement-zone", "", "New placement zone ID.")
	hostUpdateCmd.Flags().StringVar(&deplPolicyF, "deployment-policy", "", "New deployment policy ID.")
	hostUpdateCmd.Flags().BoolVar(&autoAccept, "accept", false, "Auto accept if certificate is not trusted.")
	HostsRootCmd.AddCommand(hostUpdateCmd)
}

func RunHostUpdate(args []string) (string, error) {
	var (
		address string
		ok      bool
	)
	if address, ok = ValidateArgsCount(args); !ok {
		return "", MissingHostIdError
	}
	newID, err := hosts.EditHost(address, hostName, placementZoneID, deplPolicyF, credName, autoAccept)

	if err != nil {
		return "", err
	} else {
		return "Host updated: " + newID, err
	}
}
