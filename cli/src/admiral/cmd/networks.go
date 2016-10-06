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
	"strings"

	"admiral/help"
	"admiral/networks"

	"github.com/spf13/cobra"
)

var networkIDError = errors.New("Network ID not provided.")

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
		output, err := RunNetworkCreate(args)
		processOutput(output, err)
	},
}

func initNetworkCreate() {
	networkCreateCmd.Flags().StringSliceVar(&gateways, "gateway", []string{}, "Gateway for the master subnet.")
	networkCreateCmd.Flags().StringSliceVar(&subnets, "subnet", []string{}, "Subnet in CIDR format that represents a network segment.")
	networkCreateCmd.Flags().StringSliceVar(&ipranges, "ip-range", []string{}, "Allocate container ip from a sub-range.")
	networkCreateCmd.Flags().StringSliceVar(&hostAddresses, "host", []string{}, "(Required) Hosts addresses")
	networkCreateCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	networkCreateCmd.Flags().StringVarP(&networkDriver, "driver", "d", "", "Driver to manage the Network.")
	networkCreateCmd.Flags().StringVar(&ipamDriver, "ipam-driver", "", "IPAM driver.")
	networkCreateCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	//networkCreateCmd.Flags().StringSliceVarP(&options, "opt", "o", []string{}, "Set driver options. Format: \"key:value\"")
	NetworksRootCmd.AddCommand(networkCreateCmd)
}

func RunNetworkCreate(args []string) (string, error) {
	var (
		name   string
		ok     bool
		output string
		id     string
		err    error
	)
	if name, ok = ValidateArgsCount(args); !ok {
		return "", errors.New("Network not provided.")
	}

	id, err = networks.CreateNetwork(name, networkDriver, ipamDriver, gateways, subnets, ipranges,
		custProps, hostAddresses, asyncTask)

	if !asyncTask {
		output = "Network created: " + id
	} else {
		output = "Network is being created."
	}
	return output, err

}

var networkInspectCmd = &cobra.Command{
	Use:   "inspect [NETWORK-ID]",
	Short: "Display detailed network information",
	Long:  "Display detailed network information",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunNetorkInspect(args)
		processOutput(output, err)
	},
}

func initNetworkInspect() {
	NetworksRootCmd.AddCommand(networkInspectCmd)
}

func RunNetorkInspect(args []string) (string, error) {
	var (
		id string
		ok bool
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", networkIDError
	}
	return networks.InspectNetwork(id)
}

var networkListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing networks.",
	Long:  "Lists existing networks.",
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunNetworksList(args)
		formatAndPrintOutput(output, err)
	},
}

func initNetworkList() {
	networkListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	NetworksRootCmd.AddCommand(networkListCmd)
}

func RunNetworksList(args []string) (string, error) {
	nl := networks.NetworkList{}
	_, err := nl.FetchNetworks()
	if err != nil {
		return "", err
	}
	return nl.GetOutputString(), nil
}

var networkRemoveCmd = &cobra.Command{
	Use:   "rm [NETWORK-ID]",
	Short: "Remove a network(s)",
	Long:  "Remove a network(s)",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunNetworkRemove(args)
		processOutput(output, err)
	},
}

func initNetworkRemove() {
	networkRemoveCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	NetworksRootCmd.AddCommand(networkRemoveCmd)
}

func RunNetworkRemove(args []string) (string, error) {
	if _, ok := ValidateArgsCount(args); !ok {
		return "", networkIDError
	}

	ids, err := networks.RemoveNetwork(args, asyncTask)
	var output string
	if !asyncTask {
		output = "Networks removed: " + strings.Join(ids, ", ")
	} else {
		output = "Networks are being removed."
	}
	return output, err
}
