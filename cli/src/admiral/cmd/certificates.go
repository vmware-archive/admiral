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
	"admiral/certificates"
	"fmt"

	"admiral/help"

	"github.com/spf13/cobra"
)

func init() {
	initCertAdd()
	initCertList()
	initCertRemove()
	initCertUpdate()
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

func initCertAdd() {
	certAddCmd.Flags().StringVarP(&urlF, "url", "u", "", "Url to import certificate.")
	certAddCmd.Flags().StringVarP(&dirF, "file", "f", "", "File to import certificate.")
	CertsRootCmd.AddCommand(certAddCmd)
}

var certListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing certificates",
	Long:  "Lists existing certificates",

	Run: func(cmd *cobra.Command, args []string) {
		cl := certificates.CertificateList{}
		count := cl.FetchCertificates()
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		cl.Print()
	},
}

func initCertList() {
	certListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	CertsRootCmd.AddCommand(certListCmd)
}

var certRemoveCmd = &cobra.Command{
	Use:   "rm [CERTIFICATE-ID]",
	Short: "Remove certificate.",
	Long:  "Remove certificate.",

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
		newID, err = certificates.RemoveCertificateID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Certificate removed: " + newID)
		}
	},
}

func initCertRemove() {
	CertsRootCmd.AddCommand(certRemoveCmd)
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

func initCertUpdate() {
	certUpdateCmd.Flags().StringVarP(&urlF, "url", "u", "", "Url to import certificate. (NOT IMPLEMENTED YET).")
	certUpdateCmd.Flags().StringVarP(&dirF, "file", "f", "", "File to import certificate.")
	CertsRootCmd.AddCommand(certUpdateCmd)
}
