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
	"admiral/loginout"

	"github.com/spf13/cobra"
)

func init() {
	RootCmd.AddCommand(logoutCmd)
}

var logoutCmd = &cobra.Command{
	Use:   "logout",
	Short: "Logout user",
	Long:  "Logout user",

	//Main function of the "logout" command.
	//It simply delete the file where auth token is held.
	Run: func(cmd *cobra.Command, args []string) {
		loginout.Logout()
	},
}
