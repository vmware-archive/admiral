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

	"admiral/certificates"

	"github.com/spf13/cobra"
)

func init() {
	certUpdateCmd.Flags().StringVarP(&urlF, "url", "u", "", "Url to import certificate. (NOT IMPLEMENTED YET).")
	certUpdateCmd.Flags().StringVarP(&dirF, "file", "f", "", "File to import certificate.")
	CertsRootCmd.AddCommand(certUpdateCmd)
}

var certUpdateCmd = &cobra.Command{
	Use:   "update [CERTIFICATE-ID]",
	Short: "Update certificate.",
	Long:  "Update certificate.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			ok    bool
			id    string
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter certificate ID.")
			return
		}
		newID, err = certificates.EditCertificateID(id, dirF, urlF)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Certificate updated: " + newID)
		}
	},
}
