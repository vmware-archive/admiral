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
	certAddCmd.Flags().StringVarP(&urlF, "url", "u", "", "Url to import certificate.")
	certAddCmd.Flags().StringVarP(&dirF, "file", "f", "", "File to import certificate.")
	CertsRootCmd.AddCommand(certAddCmd)
}

var certAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Add certificate",
	Long:  "Add certificate",

	Run: func(cmd *cobra.Command, args []string) {
		if urlF != "" && dirF != "" {
			fmt.Println("--file and --url flags are exclusive, provide only one of them.")
			return
		}

		var (
			id  string
			err error
		)
		if dirF != "" {
			id, err = certificates.AddFromFile(dirF)
		} else if urlF != "" {
			id, err = certificates.AddFromUrl(urlF)
		} else {
			fmt.Println("Provide url or file to add certificate.")
		}

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Certificate added: " + id)
		}
	},
}
