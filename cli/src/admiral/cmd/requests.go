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

	"admiral/requests"

	"github.com/spf13/cobra"
)

var (
	startedOnly  bool
	finishedOnly bool
	failedOnly   bool
	clearAll     bool
)

func init() {
	reqCmd.Flags().BoolVar(&clearAll, "clear", false, "Clear all logged requests.")
	reqCmd.Flags().BoolVar(&startedOnly, "started", false, "Show started only requests.")
	reqCmd.Flags().BoolVar(&finishedOnly, "finished", false, "Show finished only requests.")
	reqCmd.Flags().BoolVar(&failedOnly, "failed", false, "Show failed only requests.")
	RootCmd.AddCommand(reqCmd)
}

func allFalse() bool {
	if !startedOnly && !finishedOnly && !failedOnly {
		return true
	}
	return false
}

var reqCmd = &cobra.Command{
	Use:   "requests",
	Short: "Prints request log.",
	Long:  "Prints request log.",

	Run: func(cmd *cobra.Command, args []string) {
		RunRequest()
	},
}

func RunRequest() {
	rl := &requests.RequestsList{}
	count := rl.FetchRequests()
	if count < 1 {
		fmt.Println("n/a")
		return
	}

	if clearAll {
		rl.ClearAllRequests()
		return
	}

	if allFalse() {
		rl.PrintAll()
	} else {
		if startedOnly {
			rl.PrintStartedOnly()
		}
		if failedOnly {
			rl.PrintFailedOnly()
		}
		if finishedOnly {
			rl.PrintFinishedOnly()
		}
	}
}
