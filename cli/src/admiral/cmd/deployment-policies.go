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

	"admiral/deplPolicy"
	"admiral/help"

	"github.com/spf13/cobra"
)

var (
	MissingDeploymentPolicyIdError   = errors.New("Deployment policy ID not provided.")
	MissingDeploymentPolicyNameError = errors.New("Enter deployment policy name.")
)

const (
	DeploymentPolicyAddedMessage   = "Deployment policy added: "
	DeploymentPolicyRemovedMessage = "Deployment policy removed: "
	DeploymentPolicyUpdatedMessage = "Deployment policy updated: "
)

func init() {
	initDeploymentPolicyAdd()
	initDeploymentPolicyList()
	initDeploymentPolicyRemove()
	initDeploymentPolicyUpdate()
}

var deploymentPolicyAddCmd = &cobra.Command{
	Use:   "add [NAME]",
	Short: "Adds deployment policy.",
	Long:  "Adds deployment policy.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunDeploymentPolicyAdd(args)
		processOutput(output, err)
	},
}

func initDeploymentPolicyAdd() {
	deploymentPolicyAddCmd.Flags().StringVar(&dpDescription, "description", "", required+dpDescriptionDesc)
	DeploymentPoliciesRootCmd.AddCommand(deploymentPolicyAddCmd)
}

func RunDeploymentPolicyAdd(args []string) (string, error) {
	var (
		id     string
		err    error
		dpName string
		ok     bool
	)
	if dpName, ok = ValidateArgsCount(args); !ok {
		return "", MissingDeploymentPolicyNameError
	}
	id, err = deplPolicy.AddDP(dpName, dpDescription)

	if err != nil {
		return "", err
	} else {
		return DeploymentPolicyAddedMessage + id, err
	}
}

var deploymentPolicyListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing deployment policies.",
	Long:  "Lists existing deployment policies.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunDeploymentPolicyList(args)
		formatAndPrintOutput(output, err)
	},
}

func initDeploymentPolicyList() {
	deploymentPolicyListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	DeploymentPoliciesRootCmd.AddCommand(deploymentPolicyListCmd)
}

func RunDeploymentPolicyList(args []string) (string, error) {
	dpl := &deplPolicy.DeploymentPolicyList{}
	_, err := dpl.FetchDP()
	if err != nil {
		return "", err
	}
	return dpl.GetOutputString(), nil
}

var deploymentPolicyRemoveCmd = &cobra.Command{
	Use:   "rm [DEPLOYMENT-POLICY]",
	Short: "Removes existing depoyment policy.",
	Long:  "Removes existing depoyment policy.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunDeploymentPolicyRemove(args)
		processOutput(output, err)
	},
}

func initDeploymentPolicyRemove() {
	DeploymentPoliciesRootCmd.AddCommand(deploymentPolicyRemoveCmd)
}

func RunDeploymentPolicyRemove(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingDeploymentPolicyIdError
	}
	newID, err = deplPolicy.RemoveDPID(id)

	if err != nil {
		return "", err
	} else {
		return DeploymentPolicyRemovedMessage + newID, err
	}
}

var deploymentPolicyUpdateCmd = &cobra.Command{
	Use:   "update [DEPLOYMENT-POLICY]",
	Short: "Update deployment policy.",
	Long:  "Update deployment policy.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunDeploymentPolicyUpdate(args)
		processOutput(output, err)
	},
}

func initDeploymentPolicyUpdate() {
	deploymentPolicyUpdateCmd.Flags().StringVar(&dpDescription, "description", "", dpDescriptionDesc)
	deploymentPolicyUpdateCmd.Flags().StringVar(&dpName, "name", "", dpNameDesc)
	DeploymentPoliciesRootCmd.AddCommand(deploymentPolicyUpdateCmd)
}

func RunDeploymentPolicyUpdate(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingDeploymentPolicyIdError
	}
	newID, err = deplPolicy.EditDPID(id, dpName, dpDescription)

	if err != nil {
		return "", err
	} else {
		return DeploymentPolicyUpdatedMessage + newID, err
	}
}
