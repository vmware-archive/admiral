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
	"bufio"
	"fmt"
	"os"
	"strings"
	"syscall"

	"admiral/auth"
	"admiral/common/utils"
	"admiral/config"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh/terminal"
)

var (
	username       string
	password       string
	tokenInfo      bool
	tokenInfoDesc  = "Print information about current user."
	againstVra     bool
	againstVraDesc = "Login in Admiral instance embedded in vRA."
	tenant         string
	tenantDesc     = "Tenant."
)

func init() {
	initLogin()
	initLogout()
}

var loginCmd = &cobra.Command{
	Use:   "login",
	Short: "Login with username and pass",
	Long:  "Login with username and pass",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunLogin(args)
		processOutput(output, err)
	},
}

func initLogin() {
	loginCmd.Flags().StringVarP(&username, "user", "u", "", userNameDesc)
	loginCmd.Flags().StringVarP(&password, "pass", "p", "", passWordDesc)
	loginCmd.Flags().StringVar(&tenant, "tenant", "", vraOptional+tenantDesc)
	loginCmd.Flags().BoolVar(&againstVra, "vra", false, vraOptional+againstVraDesc)
	loginCmd.Flags().StringVar(&urlF, "url", "", "Set URL config property.")
	if !utils.IsVraMode {
		loginCmd.Flags().BoolVar(&tokenInfo, "status", false, tokenInfoDesc)
	}
	RootCmd.AddCommand(loginCmd)
}

func RunLogin(args []string) (string, error) {
	if tokenInfo {
		return auth.GetInfo(), nil
	}
	if strings.TrimSpace(username) == "" {
		if config.USER == "" {
			username = prompUsername()
		} else {
			username = config.USER
		}
	}
	if strings.TrimSpace(password) == "" {
		password = promptPassword()
	}
	url := urlRemoveTrailingSlash(urlF)
	if againstVra {
		return auth.Loginvra(username, password, tenant, url)

	}
	message, err := auth.Login(username, password, url)
	return message, err
}

//Function that prompt the user to enter his username.
func prompUsername() string {
	var username string
	reader := bufio.NewReader(os.Stdin)
	fmt.Println("Enter username:")
	username, _ = reader.ReadString('\n')
	return username
}

//Function that prompt the user to enter his password.
func promptPassword() string {
	var password string
	fmt.Println("Enter password:")
	bytePassword, _ := terminal.ReadPassword(int(syscall.Stdin))
	password = string(bytePassword)
	return password
}

var logoutCmd = &cobra.Command{
	Use:   "logout",
	Short: "Logout user",
	Long:  "Logout user",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunLogout()
		processOutput(output, err)
	},
}

func initLogout() {
	RootCmd.AddCommand(logoutCmd)
}

func RunLogout() (string, error) {
	return auth.Logout()
}
