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

	"admiral/help"
	"admiral/placementzones"

	"github.com/spf13/cobra"
)

var (
	MissingPlacementZoneIdError   = errors.New("Placement zone ID not provided.")
	MissingPlacementZoneNameError = errors.New("Placement zone name not provided.")
)

func init() {
	initPlacementZoneAdd()
	initPlacementZoneList()
	initPlacementZoneRemove()
	initPlacementZoneUpdate()
}

var placementZoneAddCmd = &cobra.Command{
	Use: "add [NAME]",

	Short: "Add placement zone by given name.",

	Long: "Add placement zone by given name.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunPlacementZoneAdd(args)
		processOutput(output, err)
	},
}

func initPlacementZoneAdd() {
	//placementZoneAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	placementZoneAddCmd.Flags().StringSliceVar(&tags, "tag", []string{}, tagsDesc)
	PlacementZonesRootCmd.AddCommand(placementZoneAddCmd)
}

func RunPlacementZoneAdd(args []string) (string, error) {
	var (
		pzName string
		ok     bool
	)
	if pzName, ok = ValidateArgsCount(args); !ok {
		return "", MissingPlacementZoneNameError
	}
	id, err := placementzones.AddPZ(pzName, custProps, tags)
	if err != nil {
		return "", err
	} else {
		return "Placement zone added: " + id, err
	}
}

var placementZoneListCmd = &cobra.Command{
	Use: "ls",

	Short: "Lists existing placement zones.",

	Long: "Lists existing placement zones.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunPlacementZoneList(args)
		formatAndPrintOutput(output, err)
	},
}

func initPlacementZoneList() {
	placementZoneListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	PlacementZonesRootCmd.AddCommand(placementZoneListCmd)
}

func RunPlacementZoneList(args []string) (string, error) {
	pzl := placementzones.PlacementZoneList{}
	_, err := pzl.FetchPZ()
	return pzl.GetOutputString(), err
}

var placementZoneRemoveCmd = &cobra.Command{
	Use: "rm [PLACEMENT-ZONE-ID]",

	Short: "Removes existing placement zone.",

	Long: "Removes existing placement zone",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunPlacementZoneRemove(args)
		processOutput(output, err)
	},
}

func initPlacementZoneRemove() {
	PlacementZonesRootCmd.AddCommand(placementZoneRemoveCmd)
}

func RunPlacementZoneRemove(args []string) (string, error) {
	var (
		err   error
		newID string
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingPlacementZoneIdError
	}
	newID, err = placementzones.RemovePZID(id)

	if err != nil {
		return "", err
	} else {
		return "Placement zone removed: " + newID, err
	}
}

var placementZoneUpdateCmd = &cobra.Command{
	Use:   "update [PLACEMENT-ZONE-ID]",
	Short: "Edit placement zone",
	Long:  "Edit placement zone",
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunPlacementZoneUpdate(args)
		processOutput(output, err)
	},
}

func initPlacementZoneUpdate() {
	placementZoneUpdateCmd.Flags().StringVar(&newName, "name", "", "New name of placement zone.")
	placementZoneUpdateCmd.Flags().StringSliceVar(&tags, "tag-add", []string{}, tagsDesc)
	placementZoneUpdateCmd.Flags().StringSliceVar(&tagsToRemove, "tag-rm", []string{}, tagsToRemoveDesc)
	PlacementZonesRootCmd.AddCommand(placementZoneUpdateCmd)
}

func RunPlacementZoneUpdate(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingPlacementZoneIdError
	}
	newID, err = placementzones.EditPZID(id, newName, tags, tagsToRemove)

	if err != nil {
		return "", err
	} else {
		return "Placement zone updated: " + newID, err
	}
}
