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

var (
	credName    string
	publicCert  string
	privateCert string
	userName    string
	passWord    string

	publicCertDesc  string = "Location to your public key."
	privateCertDesc string = "Location to your private key."
)

func init() {
	addCredCmd.Flags().StringVar(&credName, "name", "", "(Required) Credentials name.")
	//Flag after which you have to specify the location of your public key.
	addCredCmd.Flags().StringVar(&publicCert, "public", "", "(Required if using certificates)"+publicCertDesc)
	//Flag after which you have to specify the location of your private key key.
	addCredCmd.Flags().StringVar(&privateCert, "private", "", "(Required if using ceritficates)"+privateCertDesc)
	//Flag after which you have to specify the user name.
	addCredCmd.Flags().StringVar(&userName, "username", "", "(Required if using username) Username.")
	//Flag after which you have to specify password.
	addCredCmd.Flags().StringVar(&passWord, "password", "", " (Required if using username) Password.")
	//Flag for custom properties.
	addCredCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	CredentialsRootCmd.AddCommand(addCredCmd)
}

var addCredCmd = &cobra.Command{
	Use:   "add",
	Short: "Add credentials",
	Long:  "Add credentials",

	Run: func(cmd *cobra.Command, args []string) {
		if credName == "" {
			fmt.Println("Provide crendetial name.")
			return
		}
		var (
			newID string
			err   error
		)
		if userName != "" && passWord != "" {
			newID, err = credentials.AddByUsername(credName, userName, passWord, custProps)
		} else if publicCert != "" && privateCert != "" {
			newID, err = credentials.AddByCert(credName, publicCert, privateCert, custProps)
		} else {
			fmt.Println("Missing required flags.")
		}

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Credentials added: " + newID)
		}

	},
}
