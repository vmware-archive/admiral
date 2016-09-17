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

	"admiral/credentials"
	"admiral/hosts"
	"admiral/resourcePools"

	"github.com/spf13/cobra"
)

var (
	cpKeys []string
	cpVals []string
)

func init() {
	custmPropRm.Flags().StringSliceVarP(&cpKeys, "key", "k", []string{}, "(Required) Keys of custom property.")
	custmPropRm.Flags().StringVar(&cpHostIP, "host", "", "IP of the host that you want to manage custom properties.")
	custmPropRm.Flags().StringVar(&cpCredID, "credentials", "", "ID of the credentials that you want to manage custom properties.")
	custmPropRm.Flags().StringVar(&cpResPoolID, "resource-pool", "", "ID of the resource pool that you want to manage custom properties.")

	custmPropSet.Flags().StringSliceVarP(&cpKeys, "key", "k", []string{}, "(Required) Keys of custom property.")
	custmPropSet.Flags().StringSliceVarP(&cpVals, "value", "v", []string{}, "(Required) Values of custom property.")
	custmPropSet.Flags().StringVar(&cpHostIP, "host", "", "IP of the host that you want to manage custom properties.")
	custmPropSet.Flags().StringVar(&cpCredID, "credentials", "", "ID of the credentials that you want to manage custom properties.")
	custmPropSet.Flags().StringVar(&cpResPoolID, "resource-pool", "", "ID of the resource pool that you want to manage custom properties.")

	CustomPropertiesRootCmd.AddCommand(custmPropRm)
	CustomPropertiesRootCmd.AddCommand(custmPropSet)
}

var custmPropSet = &cobra.Command{
	Use:   "set",
	Short: "Set custom property to given entity.",
	Long:  "Set custom property to given entity.",

	Run: func(cmd *cobra.Command, args []string) {
		if cpHostIP != "" {
			if hosts.AddCustomProperties(cpHostIP, cpKeys, cpVals) {
				fmt.Println("Host's custom properties are set.")
			}
		}

		if cpResPoolID != "" {
			if resourcePools.AddCustomProperties(cpResPoolID, cpKeys, cpVals) {
				fmt.Println("Resource pool's custom properties are set.")
			}
		}

		if cpCredID != "" {
			if credentials.AddCustomProperties(cpCredID, cpKeys, cpVals) {
				fmt.Println("Credentials's custom properties are set.")
			}
		}
	},
}

var custmPropRm = &cobra.Command{
	Use:   "rm",
	Short: "Remove custom property to given entity.",

	Run: func(cmd *cobra.Command, args []string) {
		if cpHostIP != "" {
			if hosts.RemoveCustomProperties(cpHostIP, cpKeys) {
				fmt.Println("Host's custom properties are removed.")
			}
		}

		if cpResPoolID != "" {
			if resourcePools.RemoveCustomProperties(cpResPoolID, cpKeys) {
				fmt.Println("Resource pool's custom properties are removed.")
			}
		}

		if cpCredID != "" {
			if credentials.RemoveCustomProperties(cpCredID, cpKeys) {
				fmt.Println("Credentials's custom properties are removed.")
			}
		}
	},
}
