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
	"fmt"

	"admiral/help"
	"admiral/hosts"
	"admiral/utils"

	"os"
	"text/tabwriter"

	"admiral/endpoints/instancetypes"

	"github.com/spf13/cobra"
)

var (
	MissingHostIdError   = errors.New("Host ID not provided.")
	MissingHostNameError = errors.New("Host Name not provided.")
)

func setLongHelp() {
	hostCreateAwsCmd.Long = instancetypes.GetOutputString(instancetypes.AWS)
	hostCreateVsphereCmd.Long = instancetypes.GetOutputString(instancetypes.VSPHERE)
	hostCreateAzureCmd.Long = instancetypes.GetOutputString(instancetypes.AZURE)
}

func init() {
	initHostAdd()
	initHostDisable()
	initHostEnable()
	initHostRemove()
	initHostUpdate()
	initHostList()

	initHostCreateAws()
	initHostCreateAzure()
	initHostCreateVsphere()

}

var hostAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Add host",
	Long:  "Add host",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunAddHost(args)
		processOutput(output, err)
	},
}

func initHostAdd() {
	hostAddCmd.Flags().StringVar(&publicCert, "public", "", "*Required if adding new credentials* "+publicCertDesc)
	hostAddCmd.Flags().StringVar(&privateCert, "private", "", "*Required if adding new credentials* "+privateCertDesc)
	hostAddCmd.Flags().StringVar(&userName, "username", "", "*Required if adding new credentials* "+userNameDesc)
	hostAddCmd.Flags().StringVar(&passWord, "password", "", "*Required if adding new credentials* "+passWordDesc)
	hostAddCmd.Flags().StringVar(&ipF, "address", "", required+ipFDesc)
	hostAddCmd.Flags().StringVar(&placementZoneId, "placement-zone", "", placementZoneIdDesc)
	hostAddCmd.Flags().StringVar(&credId, "credentials", "", credIdDesc)
	hostAddCmd.Flags().StringVar(&deplPolicyF, "deployment-policy", "", deplPolicyFDesc)
	hostAddCmd.Flags().BoolVar(&autoAccept, "accept", false, autoAcceptDesc)
	hostAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	hostAddCmd.Flags().StringSliceVar(&tags, "tag", []string{}, tagsDesc)
	HostsRootCmd.AddCommand(hostAddCmd)
}

func RunAddHost(args []string) (string, error) {
	var (
		newID string
		err   error
	)
	newID, err = hosts.AddHost(ipF, placementZoneId, deplPolicyF, credId, publicCert, privateCert, userName, passWord,
		autoAccept,
		custProps, tags)

	if err != nil {
		return "", err
	}
	return "Host added: " + newID, nil

}

var hostDisableCmd = &cobra.Command{
	Use:   "disable [HOST-ID]",
	Short: "Disable host with address provided.",
	Long:  "Disable host with address provided.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunHostDisable(args)
		processOutput(output, err)
	},
}

func initHostDisable() {
	HostsRootCmd.AddCommand(hostDisableCmd)
}

func RunHostDisable(args []string) (string, error) {
	var (
		hostAddress string
		ok          bool
	)
	if hostAddress, ok = ValidateArgsCount(args); !ok {
		return "", MissingHostIdError
	}
	newID, err := hosts.DisableHost(hostAddress)

	if err != nil {
		return "", err
	}
	return "Host disabled " + newID, err

}

var hostEnableCmd = &cobra.Command{
	Use:   "enable [HOST-ID]",
	Short: "Enable host with address provided.",
	Long:  "Enable host with address provided.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunHostEnable(args)
		processOutput(output, err)
	},
}

func initHostEnable() {
	HostsRootCmd.AddCommand(hostEnableCmd)
}

func RunHostEnable(args []string) (string, error) {
	var (
		hostAddress string
		ok          bool
	)
	if hostAddress, ok = ValidateArgsCount(args); !ok {
		return "", MissingHostIdError
	}
	newID, err := hosts.EnableHost(hostAddress)

	if err != nil {
		return "", err
	}
	return "Host enabled: " + newID, err

}

var hostListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing hosts.",
	Long:  "Lists existing hosts.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunHostList(args)
		formatAndPrintOutput(output, err)
	},
}

func initHostList() {
	hostListCmd.Flags().StringVarP(&queryF, "query", "q", "", queryFDesc)
	hostListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	HostsRootCmd.AddCommand(hostListCmd)
}

func RunHostList(args []string) (string, error) {
	hl := &hosts.HostsList{}
	_, err := hl.FetchHosts(queryF)
	return hl.GetOutputString(), err
}

var hostRemoveCmd = &cobra.Command{
	Use:   "rm [HOST-ID]",
	Short: "Remove existing host.",
	Long:  "Remove existing host.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunHostRemove(args)
		processOutput(output, err)
	},
}

func initHostRemove() {
	hostRemoveCmd.Flags().BoolVar(&forceF, "force", false, forceDesc)
	hostRemoveCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	HostsRootCmd.AddCommand(hostRemoveCmd)
}

func RunHostRemove(args []string) (string, error) {
	var (
		address string
		ok      bool
	)
	if address, ok = ValidateArgsCount(args); !ok {
		return "", MissingHostIdError
	}

	if !forceF {
		fmt.Printf("Are you sure you want to remove %s? (y/n)\n", address)
		answer := utils.PromptAgreement()
		if answer == "n" || answer == "no" {
			return "", errors.New("Remove command aborted!")
		}
	}

	newID, err := hosts.RemoveHost(address, asyncTask)

	if err != nil {
		return "", err
	}
	if asyncTask {
		return "Host is being removed.", nil
	}
	return "Host removed: " + newID, err
}

var hostUpdateCmd = &cobra.Command{
	Use:   "update [HOST-ID]",
	Short: "Edit existing hosts.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunHostUpdate(args)
		processOutput(output, err)
	},
}

func initHostUpdate() {
	hostUpdateCmd.Flags().StringVar(&hostName, "name", "", "New host name.")
	hostUpdateCmd.Flags().StringVar(&credId, "credentials", "", prefixNew+credIdDesc)
	hostUpdateCmd.Flags().StringVar(&placementZoneId, "placement-zone", "", prefixNew+placementZoneIdDesc)
	hostUpdateCmd.Flags().StringVar(&deplPolicyF, "deployment-policy", "", prefixNew+deplPolicyFDesc)
	hostUpdateCmd.Flags().StringSliceVar(&tags, "tag-add", []string{}, tagsDesc)
	hostUpdateCmd.Flags().StringSliceVar(&tagsToRemove, "tag-rm", []string{}, tagsToRemoveDesc)
	hostUpdateCmd.Flags().BoolVar(&autoAccept, "accept", false, autoAcceptDesc)
	HostsRootCmd.AddCommand(hostUpdateCmd)
}

func RunHostUpdate(args []string) (string, error) {
	var (
		address string
		ok      bool
	)
	if address, ok = ValidateArgsCount(args); !ok {
		return "", MissingHostIdError
	}
	newID, err := hosts.EditHost(address, hostName, placementZoneId, deplPolicyF, credId, autoAccept, tags, tagsToRemove)

	if err != nil {
		return "", err
	} else {
		return "Host updated: " + newID, err
	}
}

var hostCreateAwsCmd = &cobra.Command{
	Use:   "aws [NAME]",
	Short: "Create docker host on AWS.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunHostCreateAws(args)
		processOutput(output, err)
	},
}

func initHostCreateAws() {
	hostCreateAwsCmd.SetOutput(tabwriter.NewWriter(os.Stdout, 5, 0, 5, ' ', 0))
	hostCreateAwsCmd.Flags().StringVar(&endpointId, "endpoint", "", required+endpointIdDesc)
	hostCreateAwsCmd.Flags().StringVar(&hostOS, "os", "", required+hostOSDesc)
	hostCreateAwsCmd.Flags().StringVar(&instanceType, "instance-type", "", required+instanceTypeDesc)
	hostCreateAwsCmd.Flags().StringVar(&guestCred, "credentials", "", guestCredDesc)
	hostCreateAwsCmd.Flags().Int32Var(&clusterSize, "cluster-size", 1, clusterSizeDesc)
	hostCreateAwsCmd.Flags().StringSliceVar(&tags, "tags", []string{}, tagsDesc)
	hostCreateAwsCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	hostCreateAwsCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	oldHelpFunc := hostCreateAwsCmd.HelpFunc()
	hostCreateAwsCmd.SetHelpFunc(func(cmd *cobra.Command, args []string) {
		hostCreateAwsCmd.Long = instancetypes.GetOutputString(instancetypes.AWS)
		hostCreateAwsCmd.SetHelpFunc(oldHelpFunc)
		hostCreateAwsCmd.Help()
	})

	HostsCreateRootCmd.AddCommand(hostCreateAwsCmd)
}

func RunHostCreateAws(args []string) (string, error) {
	var (
		name string
		ok   bool
	)
	if name, ok = ValidateArgsCount(args); !ok {
		return "", MissingHostNameError
	}
	return hosts.CreateHostAws(name, endpointId, instanceType, hostOS, guestCred, int(clusterSize), tags, custProps, asyncTask)
}

var hostCreateAzureCmd = &cobra.Command{
	Use:   "azure [NAME]",
	Short: "Create docker host on Azure.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunHostCreateAzure(args)
		processOutput(output, err)
	},
}

func initHostCreateAzure() {
	hostCreateAzureCmd.SetOutput(tabwriter.NewWriter(os.Stdout, 5, 0, 5, ' ', 0))
	hostCreateAzureCmd.Flags().StringVar(&endpointId, "endpoint", "", required+endpointIdDesc)
	hostCreateAzureCmd.Flags().StringVar(&hostOS, "os", "", required+hostOSDesc)
	hostCreateAzureCmd.Flags().StringVar(&instanceType, "instance-type", "", required+instanceTypeDesc)
	hostCreateAzureCmd.Flags().StringVar(&guestCred, "credentials", "", guestCredDesc)
	hostCreateAzureCmd.Flags().Int32Var(&clusterSize, "cluster-size", 1, clusterSizeDesc)
	hostCreateAzureCmd.Flags().StringSliceVar(&tags, "tags", []string{}, tagsDesc)
	hostCreateAzureCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	hostCreateAzureCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	oldHelpFunc := hostCreateAzureCmd.HelpFunc()
	hostCreateAzureCmd.SetHelpFunc(func(cmd *cobra.Command, args []string) {
		hostCreateAzureCmd.Long = instancetypes.GetOutputString(instancetypes.AZURE)
		hostCreateAzureCmd.SetHelpFunc(oldHelpFunc)
		hostCreateAzureCmd.Help()
	})
	HostsCreateRootCmd.AddCommand(hostCreateAzureCmd)

}

func RunHostCreateAzure(args []string) (string, error) {
	var (
		name string
		ok   bool
	)
	if name, ok = ValidateArgsCount(args); !ok {
		return "", MissingHostNameError
	}
	return hosts.CreateHostAzure(name, endpointId, instanceType, hostOS, guestCred, int(clusterSize), tags, custProps, asyncTask)
}

var hostCreateVsphereCmd = &cobra.Command{
	Use:   "vsphere [NAME]",
	Short: "Create docker host on vSphere.",

	Run: func(cmd *cobra.Command, args []string) {
		fmt.Println("hello")
		output, err := RunHostCreateVsphere(args)
		processOutput(output, err)
	},
}

func initHostCreateVsphere() {
	hostCreateVsphereCmd.SetOutput(tabwriter.NewWriter(os.Stdout, 5, 0, 5, ' ', 0))
	hostCreateVsphereCmd.Flags().StringVar(&endpointId, "endpoint", "", required+endpointIdDesc)
	hostCreateVsphereCmd.Flags().StringVar(&hostOS, "os", "", required+hostOSDesc)
	hostCreateVsphereCmd.Flags().StringVar(&instanceType, "instance-type", "", required+instanceTypeDesc)
	hostCreateVsphereCmd.Flags().StringVar(&destination, "destination", "", required+destinationDesc)
	hostCreateVsphereCmd.Flags().StringVar(&guestCred, "credentials", "", guestCredDesc)
	hostCreateVsphereCmd.Flags().Int32Var(&clusterSize, "cluster-size", 1, clusterSizeDesc)
	hostCreateVsphereCmd.Flags().StringSliceVar(&tags, "tags", []string{}, tagsDesc)
	hostCreateVsphereCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	hostCreateVsphereCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)

	oldHelpFunc := hostCreateVsphereCmd.HelpFunc()
	hostCreateVsphereCmd.SetHelpFunc(func(cmd *cobra.Command, args []string) {
		hostCreateVsphereCmd.Long = instancetypes.GetOutputString(instancetypes.VSPHERE)
		hostCreateVsphereCmd.SetHelpFunc(oldHelpFunc)
		hostCreateVsphereCmd.Help()
	})
	HostsCreateRootCmd.AddCommand(hostCreateVsphereCmd)

}

func RunHostCreateVsphere(args []string) (string, error) {
	var (
		name string
		ok   bool
	)
	if name, ok = ValidateArgsCount(args); !ok {
		return "", MissingHostNameError
	}
	return hosts.CreateHostVsphere(name, endpointId, instanceType, hostOS, destination, guestCred, int(clusterSize), tags, custProps, asyncTask)
}
