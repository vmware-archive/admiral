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

	"github.com/spf13/cobra"
)

func init() {
	regListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	RegistriesRootCmd.AddCommand(regListCmd)
}

var regListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing registries.",
	Long:  "Lists existing registries.",

	Run: func(cmd *cobra.Command, args []string) {
		rl := &registries.RegistryList{}
		rl.FetchRegistries()
		rl.Print()
	},
}
