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

	MissingRequestIdError = errors.New("Request ID not provided.")
)

func init() {
	initRequestsList()
	initRequestClear()
	initRequestRemove()
	initRequestInspect()
}

var requestsListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Prints request log.",
	Long:  "Prints request log.",

	Run: func(cmd *cobra.Command, args []string) {
		RunRequestsList()
	},
}

func initRequestsList() {
	requestsListCmd.Flags().BoolVar(&startedOnly, "started", false, startedOnlyDesc)
	requestsListCmd.Flags().BoolVar(&finishedOnly, "finished", false, finishedOnlyDesc)
	requestsListCmd.Flags().BoolVar(&failedOnly, "failed", false, failedOnlyDesc)
	RequestsRootCmd.AddCommand(requestsListCmd)
}

func RunRequestsList() {
	rl := &requests.RequestsList{}
	count, err := rl.FetchRequests()
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

func allFalse() bool {
	if !startedOnly && !finishedOnly && !failedOnly {
		return true
	}
	return false
}

var requestClearCmd = &cobra.Command{
	Use:   "clear",
	Short: "Clear all requests.",
	Long:  "Clear all requests.",

	Run: func(cmd *cobra.Command, args []string) {
		output, errs := RunRequestClear(args)
		processOutputMultiErrors(output, errs)
	},
}

func initRequestClear() {
	RequestsRootCmd.AddCommand(requestClearCmd)
}

func RunRequestClear(args []string) (string, []error) {
	rl := &requests.RequestsList{}
	rl.FetchRequests()
	return rl.ClearAllRequests()
}

var requestRemoveCmd = &cobra.Command{
	Use:   "rm [REQUEST-ID]",
	Short: "Remove specific request.",
	Long:  "Remove specific request.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunRequestRemove(args)
		processOutput(output, err)
	},
}

func initRequestRemove() {
	RequestsRootCmd.AddCommand(requestRemoveCmd)
}

func RunRequestRemove(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingRequestIdError
	}
	newID, err = requests.RemoveRequestID(id)
	return "Request removed: " + newID, err
}

var requestInspectCmd = &cobra.Command{
	Use:   "inspect [REQUEST-ID]",
	Short: "Inspect specific request.",
	Long:  "Inspect specific request.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunRequestInspect(args)
		processOutput(output, err)
	},
}

func initRequestInspect() {
	RequestsRootCmd.AddCommand(requestInspectCmd)
}

func RunRequestInspect(args []string) (string, error) {
	var (
		output string
		err    error
		id     string
		ok     bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingRequestIdError
	}

	output, err = requests.InspectRequestID(id)
	return output, err
}
