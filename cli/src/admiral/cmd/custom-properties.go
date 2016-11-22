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
	"bytes"
	"encoding/json"
	"fmt"

	"admiral/credentials"
	"admiral/hosts"
	"admiral/utils"

	"github.com/spf13/cobra"
)

func init() {
	initCustomPropertiesList()
	initCustomPropertiesSet()
	initCustomPropertiesRemove()
}

var customPropertiesListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists current properties of given entity.",
	Long:  "Lists current properties of given entity.",

	Run: func(cmd *cobra.Command, args []string) {
		output, errors := RunCustomPropertiesList(args)
		if len(errors) > 0 {
			for i := range errors {
				fmt.Println(errors[i])
			}
		} else {
			fmt.Println(output)
		}
	},
}

func initCustomPropertiesList() {
	customPropertiesListCmd.Flags().StringVar(&cpHostId, "host", "", cpHostIdDesc)
	customPropertiesListCmd.Flags().StringVar(&cpCredId, "credentials", "", cpCredIdDesc)
	//customPropertiesListCmd.Flags().StringVar(&cpPzId, "placement-zone", "", cpPzIdDesc)
	CustomPropertiesRootCmd.AddCommand(customPropertiesListCmd)
}

func RunCustomPropertiesList(args []string) (string, []error) {
	var buffer bytes.Buffer
	errors := make([]error, 0)
	if cpHostId != "" {
		if hostCp, err := hostCpString(); err == nil {
			buffer.WriteString(hostCp)
			buffer.WriteString("\n")
		} else {
			errors = append(errors, err)
		}
	}
	if cpCredId != "" {
		if credCp, err := credCpString(); err == nil {
			buffer.WriteString(credCp)
			buffer.WriteString("\n")
		} else {
			errors = append(errors, err)
		}
	}

	//if cpPzId != "" {
	//	if rpCp, err := rpCpString(); err == nil {
	//		buffer.WriteString(rpCp)
	//	} else {
	//		errors = append(errors, err)
	//	}
	//}
	return buffer.String(), errors
}

func hostCpString() (string, error) {
	cpHost, err := hosts.GetPublicCustomProperties(cpHostId)
	if err != nil {
		return "", err
	}
	cpJson, err := json.MarshalIndent(cpHost, "", "    ")
	utils.CheckBlockingError(err)
	buffer := bytes.Buffer{}
	buffer.WriteString(fmt.Sprintf("Custom Properties of Host: %s\n", cpHostId))
	buffer.WriteString(fmt.Sprint(string(cpJson)))
	return buffer.String(), nil
}

func credCpString() (string, error) {
	cpCred, err := credentials.GetPublicCustomProperties(cpCredId)
	if err != nil {
		return "", err
	}
	cpJson, err := json.MarshalIndent(cpCred, "", "    ")
	utils.CheckBlockingError(err)
	buffer := bytes.Buffer{}
	buffer.WriteString(fmt.Sprintf("Custom Properties of Credentials: %s\n", cpCredId))
	buffer.WriteString(fmt.Sprint(string(cpJson)))
	return buffer.String(), nil
}

// Currently disabled!
//func rpCpString() (string, error) {
//	cpRp, err := placementzones.GetPublicCustomProperties(cpResPoolID)
//	if err != nil {
//		return "", err
//	}
//	cpJson, err := json.MarshalIndent(cpRp, "", "    ")
//	functions.CheckJson(err)
//	buffer := bytes.Buffer{}
//	buffer.WriteString(fmt.Sprintf("Custom Properties of Placement zone: %s\n", cpCredID))
//	buffer.WriteString(fmt.Sprint(string(cpJson)))
//	return buffer.String(), nil
//}

var customPropertiesSetCmd = &cobra.Command{
	Use:   "set",
	Short: "Set custom property to given entity.",
	Long:  "Set custom property to given entity.",

	Run: func(cmd *cobra.Command, args []string) {
		output, errors := RunCustomPropertiesSet(args)
		if len(errors) > 0 {
			for i := range errors {
				fmt.Println(errors[i])
			}
		} else {
			fmt.Println(output)
		}
	},
}

func initCustomPropertiesSet() {
	customPropertiesSetCmd.Flags().StringSliceVarP(&cpKeys, "key", "k", []string{}, required+cpKeysDesc)
	customPropertiesSetCmd.Flags().StringSliceVarP(&cpVals, "value", "v", []string{}, required+cpValsDesc)
	customPropertiesSetCmd.Flags().StringVar(&cpHostId, "host", "", cpHostIdDesc)
	customPropertiesSetCmd.Flags().StringVar(&cpCredId, "credentials", "", cpCredIdDesc)
	//customPropertiesSetCmd.Flags().StringVar(&cpPzId, "placement-zone", "", cpPzIdDesc)
	CustomPropertiesRootCmd.AddCommand(customPropertiesSetCmd)
}

func RunCustomPropertiesSet(args []string) (string, []error) {
	var buffer bytes.Buffer
	errors := make([]error, 0)
	if cpHostId != "" {
		if err := hosts.AddCustomProperties(cpHostId, cpKeys, cpVals); err == nil {
			buffer.WriteString("Host's custom properties are set.")
			buffer.WriteString("\n")
		} else {
			errors = append(errors, err)
		}
	}

	// Currently disabled!
	//if cpResPoolID != "" {
	//	if err := placementzones.AddCustomProperties(cpResPoolID, cpKeys, cpVals); err == nil {
	//		buffer.WriteString("Placement zone's custom properties are set.")
	//		buffer.WriteString("\n")
	//	} else {
	//		errors = append(errors, err)
	//	}
	//}

	if cpCredId != "" {
		if err := credentials.AddCustomProperties(cpCredId, cpKeys, cpVals); err == nil {
			buffer.WriteString("Credentials's custom properties are set.")
		} else {
			errors = append(errors, err)
		}
	}
	return buffer.String(), errors
}

var customPropertiesRemoveCmd = &cobra.Command{
	Use:   "rm",
	Short: "Remove custom property to given entity.",

	Run: func(cmd *cobra.Command, args []string) {
		output, errors := RunCustomPropertiesRemove(args)
		if len(errors) > 0 {
			for i := range errors {
				fmt.Println(errors[i])
			}
		} else {
			fmt.Println(output)
		}
	},
}

func initCustomPropertiesRemove() {
	customPropertiesRemoveCmd.Flags().StringSliceVarP(&cpKeys, "key", "k", []string{}, required+cpKeysDesc)
	customPropertiesRemoveCmd.Flags().StringVar(&cpHostId, "host", "", cpHostIdDesc)
	customPropertiesRemoveCmd.Flags().StringVar(&cpCredId, "credentials", "", cpCredIdDesc)
	//customPropertiesRemoveCmd.Flags().StringVar(&cpPzId, "placement-zone", "", cpPzIdDesc)
	CustomPropertiesRootCmd.AddCommand(customPropertiesRemoveCmd)
}

func RunCustomPropertiesRemove(args []string) (string, []error) {
	var buffer bytes.Buffer
	errors := make([]error, 0)
	if cpHostId != "" {
		if err := hosts.RemoveCustomProperties(cpHostId, cpKeys); err == nil {
			buffer.WriteString("Host's custom properties are removed.")
			buffer.WriteString("\n")
		} else {
			errors = append(errors, err)
		}
	}

	// Currently disabled!
	//if cpResPoolID != "" {
	//	if err := placementzones.RemoveCustomProperties(cpResPoolID, cpKeys); err == nil {
	//		buffer.WriteString("Placement zone's custom properties are removed.")
	//		buffer.WriteString("\n")
	//	} else {
	//		errors = append(errors, err)
	//	}
	//}

	if cpCredId != "" {
		if err := credentials.RemoveCustomProperties(cpCredId, cpKeys); err == nil {
			buffer.WriteString("Credentials's custom properties are removed.")
		} else {
			errors = append(errors, err)
		}
	}
	return buffer.String(), errors
}
