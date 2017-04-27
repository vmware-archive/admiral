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
	"regexp"
	"strconv"
	"strings"

	"admiral/help"
	"admiral/placements"

	"admiral/common/utils"
	"github.com/spf13/cobra"
)

var (
	MissingPlacementIdError   = errors.New("Placement ID not provided.")
	MissingPlacementNameError = errors.New("Placement name not provided.")
	MemoryParseError          = errors.New("Unable to parse the memory provided.")
)

const (
	PlacementAddedMessage   = "Placement added: "
	PlacementRemovedMessage = "Placement removed: "
	PlacementUpdatedMessage = "Placement updated: "
)

func init() {
	initPlacementAdd()
	initPlacementUpdate()
	initPlacementList()
	initPlacementRemove()
	initPlacementInspect()
}

var placementAddCmd = &cobra.Command{
	Use:   "add [NAME]",
	Short: "Add placement",
	Long:  "Add placement",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunPlacementAdd(args)
		processOutput(output, err)
	},
}

func initPlacementAdd() {
	placementAddCmd.Flags().StringVar(&cpuShares, "cpu-shares", "", cpuSharesDesc)
	placementAddCmd.Flags().Int64Var(&instances, "instances", 0, instancesDesc)
	placementAddCmd.Flags().StringVar(&priority, "priority", "", priorityDesc)
	placementAddCmd.Flags().StringVar(&placementZoneId, "placement-zone", "", required+placementZoneIdDesc)
	placementAddCmd.Flags().StringVar(&memoryLimitStr, "memory-limit", "0kb", memoryLimitDesc)
	if !utils.IsVraMode {
		placementAddCmd.Flags().StringVar(&projectF, "project", "", projectFDesc)
	} else {
		placementAddCmd.Flags().StringVar(&projectF, "business-group", "", vraOptional+required+businessGroupIdDesc)
		placementAddCmd.Flags().StringVar(&deplPolicyF, "deployment-policy", "", deplPolicyFDesc)
	}
	PlacementsRootCmd.AddCommand(placementAddCmd)
}

func parseMemory(memory string) (int64, error) {
	reg := regexp.MustCompile("([0-9]+\\.?[0-9]*)\\s?([a-zA-Z]+)")
	results := reg.FindAllStringSubmatch(memory, -1)
	sizeStr := results[0][1]
	unit := results[0][2]
	size, err := strconv.ParseFloat(sizeStr, 64)
	if err != nil {
		return 0, err
	}
	switch strings.ToLower(unit) {
	case "kb":
		size = size * 1000
		return int64(size), nil
	case "mb":
		size = size * 1000 * 1000
		return int64(size), nil
	case "gb":
		size = size * 1000 * 1000 * 1000
		return int64(size), nil
	}
	return 0, MemoryParseError
}

func RunPlacementAdd(args []string) (string, error) {
	var (
		newID string
		err   error
		name  string
		ok    bool
	)
	if name, ok = ValidateArgsCount(args); !ok {
		return "", MissingPlacementNameError
	}
	memoryLimit, err := parseMemory(memoryLimitStr)
	if err != nil {
		return "", err
	}
	newID, err = placements.AddPlacement(name, cpuShares, priority, projectF, placementZoneId, deplPolicyF, memoryLimit, instances)
	if err != nil {
		return "", err
	} else {
		return PlacementAddedMessage + newID, err
	}
}

var placementListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing placements.",
	Long:  "Lists existing placements.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunPlacementList(args)
		formatAndPrintOutput(output, err)
	},
}

func initPlacementList() {
	placementListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	PlacementsRootCmd.AddCommand(placementListCmd)
}

func RunPlacementList(args []string) (string, error) {
	pl := &placements.PlacementList{}
	_, err := pl.FetchPlacements()
	if err != nil {
		return "", err
	}
	return pl.GetOutputString(), nil
}

var placementRemoveCmd = &cobra.Command{
	Use:   "rm [PLACEMENT]",
	Short: "Remove placement",
	Long:  "Remove placement",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunPlacementRemove(args)
		processOutput(output, err)
	},
}

func initPlacementRemove() {
	PlacementsRootCmd.AddCommand(placementRemoveCmd)
}

func RunPlacementRemove(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingPlacementIdError
	}
	newID, err = placements.RemovePlacementID(id)

	if err != nil {
		return "", err
	} else {
		return PlacementRemovedMessage + newID, err
	}
}

var placementUpdateCmd = &cobra.Command{
	Use:   "update [PLACEMENT]",
	Short: "Update placement.",
	Long:  "Update placement.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunPlacementUpdate(args)
		processOutput(output, err)
	},
}

func initPlacementUpdate() {
	placementUpdateCmd.Flags().StringVar(&newName, "name", "", "New name")
	placementUpdateCmd.Flags().Int64Var(&cpuSharesInt, "cpu-shares", -1, prefixNew+cpuSharesDesc)
	placementUpdateCmd.Flags().Int64Var(&maxNumberInstances, "instances", -1, prefixNew+instancesDesc)
	placementUpdateCmd.Flags().Int32Var(&priorityInt, "priority", -1, prefixNew+priorityDesc)
	placementUpdateCmd.Flags().StringVar(&projectF, "project", "", prefixNew+projectFDesc)
	placementUpdateCmd.Flags().StringVar(&placementZoneId, "placement-zone", "", prefixNew+placementZoneIdDesc)
	placementUpdateCmd.Flags().StringVar(&deplPolicyF, "deployment-policy", "", prefixNew+deplPolicyFDesc)
	placementUpdateCmd.Flags().StringVar(&memoryLimitStr, "memory-limit", "0kb", prefixNew+memoryLimitDesc)
	PlacementsRootCmd.AddCommand(placementUpdateCmd)
}

func RunPlacementUpdate(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)
	memoryLimit, err := parseMemory(memoryLimitStr)
	if err != nil {
		return "", err
	}

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingPlacementIdError
	}
	newID, err = placements.EditPlacementID(id, newName, projectF, placementZoneId, deplPolicyF, priorityInt, cpuSharesInt, maxNumberInstances, memoryLimit)

	if err != nil {
		return "", err
	} else {
		return PlacementUpdatedMessage + newID, err
	}
}

var placementInspectCmd = &cobra.Command{
	Use:   "inspect [PLACEMENT]",
	Short: "Inspect placement.",
	Long:  "Inspect placement.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunPlacementInspect(args)
		processOutput(output, err)
	},
}

func initPlacementInspect() {
	PlacementsRootCmd.AddCommand(placementInspectCmd)
}

func RunPlacementInspect(args []string) (string, error) {
	var (
		id string
		ok bool
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingPlacementIdError
	}
	return placements.InspectPlacement(id)
}
