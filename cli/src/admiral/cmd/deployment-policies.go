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
	"admiral/deplPolicy"
	"fmt"

	"admiral/help"

	"github.com/spf13/cobra"
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
		var (
			id     string
			err    error
			dpName string
			ok     bool
		)
		if dpName, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter deployment policy name.")
			return
		}
		id, err = deplPolicy.AddDP(dpName, dpDescription)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Deployment policy added: " + id)
		}
	},
}

func initDeploymentPolicyAdd() {
	deploymentPolicyAddCmd.Flags().StringVar(&dpDescription, "description", "", "(Required) Deployment policy description.")
	DeploymentPoliciesRootCmd.AddCommand(deploymentPolicyAddCmd)
}

var deploymentPolicyListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing deployment policies.",
	Long:  "Lists existing deployment policies.",

	Run: func(cmd *cobra.Command, args []string) {
		dpl := &deplPolicy.DeploymentPolicyList{}
		count := dpl.FetchDP()
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		dpl.Print()
	},
}

func initDeploymentPolicyList() {
	deploymentPolicyListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	DeploymentPoliciesRootCmd.AddCommand(deploymentPolicyListCmd)
}

var deploymentPolicyRemoveCmd = &cobra.Command{
	Use:   "rm [DEPLOYMENT-POLICY-ID]",
	Short: "Removes existing depoyment policy.",
	Long:  "Removes existing depoyment policy.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter deployment policy ID.")
			return
		}
		newID, err = deplPolicy.RemoveDPID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Deployment policy removed: " + newID)
		}
	},
}

func initDeploymentPolicyRemove() {
	DeploymentPoliciesRootCmd.AddCommand(deploymentPolicyRemoveCmd)
}

var deploymentPolicyUpdateCmd = &cobra.Command{
	Use:   "update [DEPLOYMENT-POLICY-ID]",
	Short: "Update deployment policy.",
	Long:  "Update deployment policy.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter deployment policy ID.")
			return
		}
		newID, err = deplPolicy.EditDPID(id, dpName, dpDescription)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Deployment policy updated: " + newID)
		}
	},
}

func initDeploymentPolicyUpdate() {
	deploymentPolicyUpdateCmd.Flags().StringVar(&dpDescription, "description", "", "(Required) New deployment policy description.")
	deploymentPolicyUpdateCmd.Flags().StringVar(&dpName, "name", "", "(Required) New deployment policy name")
	DeploymentPoliciesRootCmd.AddCommand(deploymentPolicyUpdateCmd)
}
