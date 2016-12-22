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

	"admiral/closures"

	"github.com/spf13/cobra"
)

var MissingClosureIdError = errors.New("Closure ID not provided.")

const (
	ClosureRemovedMessage = "Closure removed: "
)

func init() {
	initClosuresList()
	initClosuresRemove()
}

var closuresListCmd = &cobra.Command{
	Use:   "ls",
	Short: "List closures.",
	Long:  "List closures.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunClosureList(args)
		formatAndPrintOutput(output, err)
	},
}

func initClosuresList() {
	ClosuresRootCmd.AddCommand(closuresListCmd)
}

func RunClosureList(args []string) (string, error) {
	cl := &closures.ClosureList{}
	_, err := cl.FetchClosures()
	output := cl.GetOutputString()
	return output, err
}

var closuresRemoveCmd = &cobra.Command{
	Use:   "rm",
	Short: "Remove closure.",
	Long:  "Remove closure.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunClosureRemove(args)
		processOutput(output, err)
	},
}

func initClosuresRemove() {
	ClosuresRootCmd.AddCommand(closuresRemoveCmd)
}

func RunClosureRemove(args []string) (string, error) {
	var (
		id string
		ok bool
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingClosureIdError
	}
	id, err := closures.RemoveClosure(id)

	return ClosureRemovedMessage + id, err
}
