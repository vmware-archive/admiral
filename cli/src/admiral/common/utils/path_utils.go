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

package utils

import (
	"fmt"
	"github.com/mitchellh/go-homedir"
	"os"
	"runtime"
)

const WINDOWS_PATH_SEPARATOR = "\\"

const UNIX_PATH_SEPARATOR = "/"

// TokenPath returns the path to the file that contain the auth token.
func TokenPath() string {
	return CliDir() + "admiral-cli.token"
}

// ConfigPath returns the path to the file that contain CLI configurations.
func ConfigPath() string {
	return CliDir() + "admiral-cli.config"
}

// TrustedCertsPath returns the path to the file that contain
// certificates trusted from the user.
func TrustedCertsPath() string {
	return CliDir() + "trusted-certs.pem"
}

// CliDir returns the path to the CLI directory where config, token and other
// files are being kept.
func CliDir() string {
	home := GetHome()
	if runtime.GOOS == "windows" {
		return home + WINDOWS_PATH_SEPARATOR + ".admiral-cli" + WINDOWS_PATH_SEPARATOR
	} else {
		return home + UNIX_PATH_SEPARATOR + ".admiral-cli" + UNIX_PATH_SEPARATOR
	}
}

// MkCliDir makes CLI directory.
func MkCliDir() bool {
	err := os.MkdirAll(CliDir(), 0777)
	if err != nil {
		fmt.Println(err.Error())
		return false
	}
	return true
}

// GetHome returns home directory of the current OS.
func GetHome() string {
	home, err := homedir.Dir()
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(0)
	}
	return home
}
