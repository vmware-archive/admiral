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

const (
	PlacementZoneAddedMessage   = "Placement zone added: "
	PlacementZoneRemovedMessage = "Placement zone removed: "
	PlacementZoneUpdatedMessage = "Placement zone updated: "
)

func init() {
	initPlacementZoneAdd()
	initPlacementZoneList()
	initPlacementZoneRemove()
	initPlacementZoneUpdate()
}

var placementZoneAddCmd = &cobra.Command{
	Use:   "add [NAME]",
	Short: "Add placement zone by given name.",
	Long:  "Add placement zone by given name.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunPlacementZoneAdd(args)
		processOutput(output, err)
	},
}

func initPlacementZoneAdd() {
	//placementZoneAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	placementZoneAddCmd.Flags().StringSliceVarP(&tags, "tag", "t", []string{}, tagsDesc)
	placementZoneAddCmd.Flags().StringSliceVarP(&tagsToMatch, "tag-to-match", "T", []string{}, tagsToMatchDesc)
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
	id, err := placementzones.AddPZ(pzName, custProps, tags, tagsToMatch)
	if err != nil {
		return "", err
	} else {
		return PlacementZoneAddedMessage + id, err
	}
}

var placementZoneListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing placement zones.",
	Long:  "Lists existing placement zones.",

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
	Use:   "rm [PLACEMENT-ZONE]",
	Short: "Removes existing placement zone.",
	Long:  "Removes existing placement zone",

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
		return PlacementZoneRemovedMessage + newID, err
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
	placementZoneUpdateCmd.Flags().StringSliceVarP(&tags, "tag-add", "t", []string{}, tagsDesc)
	placementZoneUpdateCmd.Flags().StringSliceVar(&tagsToRemove, "tag-rm", []string{}, tagsToRemoveDesc)
	placementZoneUpdateCmd.Flags().StringSliceVarP(&tagsToMatch, "tag-to-match-add", "T", []string{}, tagsToMatchDesc)
	placementZoneUpdateCmd.Flags().StringSliceVar(&tagsToMatchToRemove, "tag-to-match-rm", []string{}, tagsToMatchToRemoveDesc)
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
	newID, err = placementzones.EditPZID(id, newName, tags, tagsToRemove, tagsToMatch, tagsToMatchToRemove)

	if err != nil {
		return "", err
	} else {
		return PlacementZoneUpdatedMessage + newID, err
	}
}
