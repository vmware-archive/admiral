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

	"github.com/spf13/cobra"
)

func init() {
	CredentialsRootCmd.AddCommand(removeCredCmd)
}

var removeCredCmd = &cobra.Command{
	Use:   "rm [CREDENTIALS-ID]",
	Short: "Removes existing credentials.",
	Long:  "Removes existing credentials.",

	//Main function for the "rm-cred" command.
	//It takes credential name as args which.
	//If name is duplicate for other credentials, command is aborted.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			ok    bool
			id    string
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter credentials ID.")
			return
		}
		newID, err = credentials.RemoveCredentialsID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Credentials removed: " + newID)
		}
	},
}
