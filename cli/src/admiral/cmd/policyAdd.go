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
	"regexp"
	"strconv"
	"strings"

	"admiral/policies"

	"github.com/spf13/cobra"
)

var (
	cpuShares      string
	instances      string
	priority       string
	tenants        string
	resPoolID      string
	deplPolID      string
	memoryLimitStr string
)

func init() {
	polAddCmd.Flags().StringVar(&cpuShares, "cpu", "", "CPU shares.")
	polAddCmd.Flags().StringVar(&instances, "instances", "", "(Required) Instances")
	polAddCmd.Flags().StringVar(&priority, "prio", "", "Priority")
	polAddCmd.Flags().StringVar(&tenants, "group", "", "(Required) Group")
	polAddCmd.Flags().StringVar(&resPoolID, "resource-pool", "", "(Required) Resource pool ID")
	polAddCmd.Flags().StringVar(&deplPolID, "deployment-policy", "", "(Required) Deployment policy ID")
	polAddCmd.Flags().StringVar(&memoryLimitStr, "memory", "0kb", "Memory limit. Default unit: kb. Units supported: kb/mb/gb. Example: 1024mb")
	PoliciesRootCmd.AddCommand(polAddCmd)
}

var polAddCmd = &cobra.Command{
	Use:   "add [NAME]",
	Short: "Add policy",
	Long:  "Add policy",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			name  string
			ok    bool
		)
		if name, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter policy name.")
			return
		}
		memoryLimit, err := parseMemory(memoryLimitStr)
		if err != nil {
			fmt.Println(err)
			return
		}
		newID, err = policies.AddPolicy(name, cpuShares, instances, priority, tenants, resPoolID, deplPolID, memoryLimit)
		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Policy added: " + newID)
		}
	},
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
