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

	"admiral/track"

	"os"

	"github.com/spf13/cobra"
)

func init() {
	RootCmd.AddCommand(waitCmd)
}

var waitCmd = &cobra.Command{
	Use:   "wait [TASK-ID]",
	Short: "Wait until task has finished.",
	Long:  "Wait until task has finished.",

	//Main function for the "wait" command.
	//This command is used to wait for specific task to be finished.
	//It takes the track ID as parameter where specific commands provide it after they are called.
	//Every 3 seconds it will check for the status of the given task.
	//When it's either FAILED or FINISHED or CANELLED it will end with printing the status of the task.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			taskId string
			ok     bool
		)
		if taskId, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter task id.")
			return
		}
		_, err := track.Wait(taskId)
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
		}
	},
}
