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
	"admiral/utils"

	"fmt"

	"github.com/spf13/cobra"
)

var ShowVersion bool
var (
	defaultVersion string = "${admiral.build.tag}"
	version        string
)

func init() {
	AutocompleteCmd.Hidden = true
	RootCmd.AddCommand(AppsRootCmd, CertsRootCmd, CredentialsRootCmd,
		DeploymentPoliciesRootCmd, HostsRootCmd, PlacementsRootCmd,
		PlacementZonesRootCmd, TemplatesRootCmd, RegistriesRootCmd,
		NetworksRootCmd, CustomPropertiesRootCmd, AutocompleteCmd,
		ProjectsRootCmd, RequestsRootCmd)

	RootCmd.Flags().BoolVar(&ShowVersion, "version", false, "Admiral CLI Version.")
	RootCmd.PersistentFlags().BoolVar(&utils.Verbose, "verbose", false, "Showing every request/response json body.")
	RootCmd.PersistentFlags().StringVar(&utils.TokenFromFlagVar, "token", "", tokenDesc)
	RootCmd.SetUsageTemplate(help.DefaultUsageTemplate)
}

//Root command which add every other commands, but can't be used as standalone.
var RootCmd = &cobra.Command{
	Use:   "admiral",
	Short: "Admiral CLI",
	Long:  "For more information about the Admiral CLI visit https://github.com/vmware/admiral/wiki/CLI-guide",
	Run: func(cmd *cobra.Command, args []string) {
		if !ShowVersion {
			cmd.Help()
			return
		}
		var versionToPrint string
		if version == "" {
			versionToPrint = defaultVersion
		} else {
			versionToPrint = version
		}
		fmt.Printf(admiralLogo, versionToPrint)
	},
}

var AppsRootCmd = &cobra.Command{
	Use:   "app",
	Short: "Perform operations with applications.",
}

var CertsRootCmd = &cobra.Command{
	Use:   "cert",
	Short: "Perform operations with certificates.",
}

var ConfigRootCmd = &cobra.Command{
	Use:   "config",
	Short: "Set and get configuration properties.",
}

var CredentialsRootCmd = &cobra.Command{
	Use:   "credentials",
	Short: "Perform operations with credentials.",
}

var DeploymentPoliciesRootCmd = &cobra.Command{
	Use:   "deployment-policy",
	Short: "Perform operations with deployment policies.",
}

var HostsRootCmd = &cobra.Command{
	Use:   "host",
	Short: "Perform operations with hosts.",
}

var PlacementsRootCmd = &cobra.Command{
	Use:   "placement",
	Short: "Perform operations with placements.",
}

var PlacementZonesRootCmd = &cobra.Command{
	Use:   "placement-zone",
	Short: "Perform operations with placement zones.",
}

var TemplatesRootCmd = &cobra.Command{
	Use:   "template",
	Short: "Perform operations with templates.",
}

var RegistriesRootCmd = &cobra.Command{
	Use:   "registry",
	Short: "Perform operations with registries.",
}

var NetworksRootCmd = &cobra.Command{
	Use:   "network",
	Short: "Perform operations with netoworks.",
}

var CustomPropertiesRootCmd = &cobra.Command{
	Use:   "custom-properties",
	Short: "Perform opertaions with custom properties.",
}

var ProjectsRootCmd = &cobra.Command{
	Use:   "project",
	Short: "Perform operations with projects.",
}

var RequestsRootCmd = &cobra.Command{
	Use:   "requests",
	Short: "Perform operations with requests.",
}

var AutocompleteCmd = &cobra.Command{
	Use:   "autocomplete",
	Short: "Generate autocomplete file. It is generated in home/.admiral-cli",
	Run: func(cmd *cobra.Command, args []string) {
		RootCmd.GenBashCompletionFile(utils.CliDir() + "/admiral-cli-autocomplete.sh")
	},
}
