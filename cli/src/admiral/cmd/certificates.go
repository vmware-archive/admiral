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

	"admiral/certificates"
	"admiral/help"

	"github.com/spf13/cobra"
)

var (
	MissingCertIdError        = errors.New("Certificate ID not provided.")
	MissingUrlOrFileCertError = errors.New("Url or file to add certificate not provided.")
	ExclusiveFlagsError       = errors.New("--file and --url flags are exclusive, provide only one of them.")
)

const (
	CertificateAddedMessage   = "Certificate added: "
	CertificateRemovedMessage = "Certificate removed: "
	CertificateUpdatedMessage = "Certificate updated: "
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
		output, err := RunCertAdd(args)
		processOutput(output, err)
	},
}

func initCertAdd() {
	certAddCmd.Flags().StringVarP(&urlF, "url", "u", "", "Url to import certificate.")
	certAddCmd.Flags().StringVarP(&dirF, "file", "f", "", "File to import certificate.")
	CertsRootCmd.AddCommand(certAddCmd)
}

func RunCertAdd(args []string) (string, error) {
	if urlF != "" && dirF != "" {
		return "", ExclusiveFlagsError
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
		return "", MissingUrlOrFileCertError
	}

	if err != nil {
		return "", err
	} else {
		return CertificateAddedMessage + id, err
	}
}

var certListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing certificates",
	Long:  "Lists existing certificates",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunCertList(args)
		formatAndPrintOutput(output, err)
	},
}

func initCertList() {
	certListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	CertsRootCmd.AddCommand(certListCmd)
}

func RunCertList(args []string) (string, error) {
	cl := certificates.CertificateList{}
	_, err := cl.FetchCertificates()
	if err != nil {
		return "", err
	}
	return cl.GetOutputString(), err
}

var certRemoveCmd = &cobra.Command{
	Use:   "rm [CERTIFICATE]",
	Short: "Remove certificate.",
	Long:  "Remove certificate.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunCertRemove(args)
		processOutput(output, err)
	},
}

func initCertRemove() {
	CertsRootCmd.AddCommand(certRemoveCmd)
}

func RunCertRemove(args []string) (string, error) {
	var (
		newID string
		err   error
		ok    bool
		id    string
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingCertIdError
	}
	newID, err = certificates.RemoveCertificateID(id)

	if err != nil {
		return "", err
	} else {
		return CertificateRemovedMessage + newID, err
	}
}

var certUpdateCmd = &cobra.Command{
	Use:   "update [CERTIFICATE]",
	Short: "Update certificate.",
	Long:  "Update certificate.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunCertUpdate(args)
		processOutput(output, err)
	},
}

func initCertUpdate() {
	certUpdateCmd.Flags().StringVarP(&urlF, "url", "u", "", "Url to import certificate. (NOT IMPLEMENTED YET).")
	certUpdateCmd.Flags().StringVarP(&dirF, "file", "f", "", "File to import certificate.")
	CertsRootCmd.AddCommand(certUpdateCmd)
}

func RunCertUpdate(args []string) (string, error) {
	var (
		newID string
		err   error
		ok    bool
		id    string
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingCertIdError
	}
	newID, err = certificates.EditCertificateID(id, dirF, urlF)

	if err != nil {
		return "", err
	} else {
		return CertificateUpdatedMessage + newID, err
	}
}
