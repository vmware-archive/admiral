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
	startedOnly      bool
	startedOnlyDesc  = "Show started only requests."
	finishedOnly     bool
	finishedOnlyDesc = "Show finished only requests."
	failedOnly       bool
	failedOnlyDesc   = "Show failed only requests."
)

func init() {
	reqCmd.Flags().BoolVar(&clearAll, "clear", false, clearAllReqDesc)
	reqCmd.Flags().BoolVar(&startedOnly, "started", false, startedOnlyDesc)
	reqCmd.Flags().BoolVar(&finishedOnly, "finished", false, finishedOnlyDesc)
	reqCmd.Flags().BoolVar(&failedOnly, "failed", false, failedOnlyDesc)
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
	count, err := rl.FetchRequests()
	if clearAll {
		rl.ClearAllRequests()
		return
	}
	if err != nil {
		fmt.Println(err)
		return
	}

	if count < 1 {
		fmt.Println("n/a")
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
