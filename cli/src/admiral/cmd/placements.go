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

	"github.com/spf13/cobra"
)

var placementIdError = errors.New("Placement ID not provided.")

func init() {
	initPlacementAdd()
	initPlacementUpdate()
	initPlacementList()
	initPlacementRemove()
}

var (
	cpuShares      string
	instances      string
	priority       string
	tenants        string
	resPoolID      string
	deplPolID      string
	memoryLimitStr string

	priorityInt        int32
	maxNumberInstances int32
	cpuSharesInt       int32
)

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
	placementAddCmd.Flags().StringVar(&cpuShares, "cpu", "", "CPU shares.")
	placementAddCmd.Flags().StringVar(&instances, "instances", "", "Instances")
	placementAddCmd.Flags().StringVar(&priority, "priority", "", "Priority")
	placementAddCmd.Flags().StringVar(&tenants, "project", "", "Project")
	placementAddCmd.Flags().StringVar(&resPoolID, "resource-pool", "", "(Required) Resource pool ID")
	placementAddCmd.Flags().StringVar(&deplPolID, "deployment-policy", "", "Deployment policy ID")
	placementAddCmd.Flags().StringVar(&memoryLimitStr, "memory", "0kb", "Memory limit. Default unit: kb. Units supported: kb/mb/gb. Example: 1024mb")
	PlacementsRootCmd.AddCommand(placementAddCmd)
}

func parseMemory(memory string) (size int64, err error) {
	reg := regexp.MustCompile("([0-9]+)([a-zA-Z]+)")
	results := reg.FindAllStringSubmatch(memory, -1)
	sizeStr := results[0][1]
	unit := results[0][2]
	size, err = strconv.ParseInt(sizeStr, 10, 64)
	switch strings.ToLower(unit) {
	case "kb":
		size = size * 1000
		return
	case "mb":
		size = size * 1000 * 1000
		return
	case "gb":
		size = size * 1000 * 1000 * 1000
		return
	}
	return 0, errors.New("Unable to parse the memory provided.")
}

func RunPlacementAdd(args []string) (string, error) {
	var (
		newID string
		err   error
		name  string
		ok    bool
	)
	if name, ok = ValidateArgsCount(args); !ok {
		return "", errors.New("Placement name not provided.")
	}
	memoryLimit, err := parseMemory(memoryLimitStr)
	if err != nil {
		return "", err
	}
	newID, err = placements.AddPlacement(name, cpuShares, instances, priority, tenants, resPoolID, deplPolID, memoryLimit)
	if err != nil {
		return "", err
	} else {
		return "Placement added: " + newID, err
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
	Use:   "rm [PLACEMENT-ID]",
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
		return "", placementIdError
	}
	newID, err = placements.RemovePlacementID(id)

	if err != nil {
		return "", err
	} else {
		return "Placement removed: " + newID, err
	}
}

var placementUpdateCmd = &cobra.Command{
	Use:   "update [PLACEMENT-ID]",
	Short: "Update placement.",
	Long:  "Update placement.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunPlacementUpdate(args)
		processOutput(output, err)
	},
}

func initPlacementUpdate() {
	placementUpdateCmd.Flags().StringVar(&newName, "name", "", "New name")
	placementUpdateCmd.Flags().Int32Var(&cpuSharesInt, "cpu", -1, "New CPU shares.")
	placementUpdateCmd.Flags().Int32Var(&maxNumberInstances, "instances", -1, "New instances")
	placementUpdateCmd.Flags().Int32Var(&priorityInt, "prio", -1, "New priority")
	placementUpdateCmd.Flags().StringVar(&tenants, "group", "", "New group")
	placementUpdateCmd.Flags().StringVar(&resPoolID, "resource-pool", "", "New resource pool ID")
	placementUpdateCmd.Flags().StringVar(&deplPolID, "deployment-policy", "", "New deployment policy ID")
	placementUpdateCmd.Flags().StringVar(&memoryLimitStr, "memory", "0kb", "New memory limit. Default unit: kb. Units supported: kb/mb/gb. Example: 1024mb")
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
		return "", placementIdError
	}
	newID, err = placements.EditPlacementID(id, newName, tenants, resPoolID, deplPolID, cpuSharesInt, maxNumberInstances, priorityInt, memoryLimit)

	if err != nil {
		return "", err
	} else {
		return "Placement updated: " + newID, err
	}
}
