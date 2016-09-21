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

	"admiral/help"
	"admiral/network"

	"github.com/spf13/cobra"
)

var (
	gateways []string
	subnets  []string
	options  []string
)

func init() {
	initNetworkCreate()
	initNetworkInspect()
	initNetworkList()
	initNetworkRemove()
}

var networkCreateCmd = &cobra.Command{
	Use:   "create [NAME]",
	Short: "Create a network",
	Long:  "Create a network",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			name string
			ok   bool
		)
		if name, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter network.")
			return
		}
		n := &network.Network{}
		n.SetName(name)
		n.SetOptions(options)
		n.SetIPAMConfig(subnets, gateways)
		isCreated, msg := n.Create()
		if isCreated {
			fmt.Println("Network created successfully.")
		} else {
			fmt.Println("Error when creating network.")
			if msg != "" {
				fmt.Println(msg)
			}
		}
	},
}

func initNetworkCreate() {
	networkCreateCmd.Flags().StringSliceVar(&gateways, "gateway", []string{}, "Gateway for the master subnet.")
	networkCreateCmd.Flags().StringSliceVar(&subnets, "subnet", []string{}, "Subnet in CIDR format that represents a network segment.")
	networkCreateCmd.Flags().StringSliceVarP(&options, "opt", "o", []string{}, "Set driver options. Format: \"key:value\"")
	networkCreateCmd.SetHelpTemplate(help.NetworkUsageTmpl)
	NetworksRootCmd.AddCommand(networkCreateCmd)
}

var networkInspectCmd = &cobra.Command{
	Use:   "inspect [NETWORK-NAME]",
	Short: "Display detailed network information",
	Long:  "Display detailed network information",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			name string
			ok   bool
		)
		if name, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter network.")
			return
		}
		found, str := network.InspectNetwork(name)
		if !found {
			fmt.Println("Network not found.")
			return
		}
		fmt.Println(str)
	},
}

func initNetworkInspect() {
	NetworksRootCmd.AddCommand(networkInspectCmd)
}

var networkListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing networks.",
	Long:  "Lists existing networks.",
	Run: func(cmd *cobra.Command, args []string) {
		nl := network.NetworkList{}
		count := nl.FetchNetworks()
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		nl.Print()
	},
}

func initNetworkList() {
	networkListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	NetworksRootCmd.AddCommand(networkListCmd)
}

var networkRemoveCmd = &cobra.Command{
	Use:   "rm [NETWORK-NAME]",
	Short: "Remove a network",
	Long:  "Remove a network",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			name string
			ok   bool
		)
		if name, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter network.")
			return
		}
		isRemoved := network.RemoveNetwork(name)
		if isRemoved {
			fmt.Println("Network removed successfully.")
		}
	},
}

func initNetworkRemove() {
	NetworksRootCmd.AddCommand(networkRemoveCmd)
}
