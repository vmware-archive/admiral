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
	"strings"

	"admiral/logs"

	"github.com/spf13/cobra"
)

var since int32

func init() {
	logsCmd.Flags().Int32VarP(&since, "since", "s", 15, "Since when to show logs.")
	RootCmd.AddCommand(logsCmd)
}

var logsCmd = &cobra.Command{
	Use:   "logs [CONTAINER]",
	Short: "Fetch the logs of a container",
	Long:  "Fetch the logs of a container",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunLogs(args)
		processOutput(output, err)
	},
}

func RunLogs(args []string) (string, error) {
	contName := strings.Join(args, " ")
	sinceSecs := since * 60
	return logs.GetLog(contName, string(sinceSecs))
}
