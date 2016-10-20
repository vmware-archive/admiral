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

	"admiral/apps"
	"admiral/help"

	"github.com/spf13/cobra"
)

var MissingAppIdError = errors.New("Application ID not provided.")

func init() {
	initAppInspect()
	initAppList()
	initAppRemove()
	initAppRestart()
	initAppRun()
	initAppStart()
	initAppStop()
}

var appInspectCmd = &cobra.Command{
	Use:   "inspect [APPLICATION-ID]",
	Short: "Inspect application for additional info.",
	Long:  "Inspect application for additional info.",

	Run: func(cmd *cobra.Command, args []string) {
		err := RunAppInspect(args)
		if err != nil {
			fmt.Println(err)
		}
	},
}

func initAppInspect() {
	AppsRootCmd.AddCommand(appInspectCmd)
}

func RunAppInspect(args []string) error {
	var (
		id string
		ok bool
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return MissingAppIdError
	}
	apps.InspectID(id)
	return nil
}

var appListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing apps",
	Long:  "Lists existing applications.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunAppList(args)
		formatAndPrintOutput(output, err)
	},
}

func initAppList() {
	appListCmd.Flags().BoolVarP(&inclCont, "containers", "c", false, inclContDesc)
	appListCmd.Flags().StringVarP(&queryF, "query", "q", "", queryFDesc)

	appListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	AppsRootCmd.AddCommand(appListCmd)
}

func RunAppList(args []string) (string, error) {
	la := apps.ListApps{}
	_, err := la.FetchApps(queryF)
	if err != nil {
		return "", err
	}
	if inclCont {
		return la.GetOutputStringWithContainers(), err
	}
	return la.GetOutputStringWithoutContainers(), nil

}

var appRemoveCmd = &cobra.Command{
	Use:   "rm [APPLICATION-ID]",
	Short: "Stops existing application",
	Long:  "Stops existing application",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunAppRemove(args)
		processOutput(output, err)
	},
}

func initAppRemove() {
	appRemoveCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	AppsRootCmd.AddCommand(appRemoveCmd)
}

func RunAppRemove(args []string) (string, error) {
	var (
		IDs []string
		err error
		ok  bool
		id  string
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingAppIdError
	}
	IDs, err = apps.RemoveAppID(id, asyncTask)

	if err != nil {
		return "", err
	} else if len(IDs) > 0 {
		var output string
		if asyncTask {
			output = "Application is being removed: " + IDs[0]
		} else {
			output = "Application removed: " + IDs[0]
		}
		return output, err
	}
	return "", err
}

var appRestartCmd = &cobra.Command{
	Use:   "restart [APPLICATION-ID]",
	Short: "Restarts application.",
	Long:  "Restarts application.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunAppRestart(args)
		processOutput(output, err)
	},
}

func initAppRestart() {
	appRestartCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	AppsRootCmd.AddCommand(appRestartCmd)
}

func RunAppRestart(args []string) (string, error) {
	var (
		IDs []string
		err error
		ok  bool
		id  string
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingAppIdError
	}
	IDs, err = apps.StopAppID(id, asyncTask)
	IDs, err = apps.StartAppID(id, asyncTask)

	if err != nil {
		return "", err
	} else if len(IDs) > 0 {
		var output string
		if asyncTask {
			output = "Application is being restarted: " + IDs[0]
		} else {
			output = "Application restarted: " + IDs[0]
		}
		return output, err
	}
	return "", err
}

var appRunCmd = &cobra.Command{
	Use:   "run [TEMPLATE-ID]",
	Short: "Provision application from template.",
	Long:  "Provision application from template.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunAppRun(args)
		processOutput(output, err)
	},
}

func initAppRun() {
	appRunCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	appRunCmd.Flags().StringVar(&dirF, "file", "", "Provision template from file.")
	appRunCmd.Flags().BoolVar(&keepTemplate, "keep", false, keepTemplateDesc)
	appRunCmd.Flags().StringVar(&projectF, "project", "", required+projectFDesc)
	AppsRootCmd.AddCommand(appRunCmd)
}

func RunAppRun(args []string) (string, error) {
	var (
		IDs []string
		err error
		ok  bool
		id  string
	)

	if dirF != "" {
		IDs, err = apps.RunAppFile(dirF, keepTemplate, asyncTask)
	} else {
		if id, ok = ValidateArgsCount(args); !ok {
			return "", MissingTemplateIdError
		}
		IDs, err = apps.RunAppID(id, asyncTask)
	}

	if err != nil {
		return "", err
	} else if len(IDs) > 0 {
		var output string
		if asyncTask {
			output = "Application is being provisioned: " + IDs[0]
		} else {
			output = "Application provisioned: " + IDs[0]
		}
		return output, err
	}
	return "", err
}

var appStartCmd = &cobra.Command{
	Use:   "start [APPLICATION-ID]",
	Short: "Starts existing application",
	Long:  "Starts existing application",

	//Main function for "start-app" command.
	//For arguments take application names.
	//If any of the name is non-unique the command will be aborted.
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunAppStart(args)
		processOutput(output, err)
	},
}

func initAppStart() {
	appStartCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	AppsRootCmd.AddCommand(appStartCmd)
}

func RunAppStart(args []string) (string, error) {
	var (
		IDs []string
		err error
		ok  bool
		id  string
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingAppIdError
	}
	IDs, err = apps.StartAppID(id, asyncTask)

	if err != nil {
		return "", err
	} else if len(IDs) > 0 {
		var output string
		if asyncTask {
			output = "Application is being started: " + IDs[0]
		} else {
			output = "Application started: " + IDs[0]
		}
		return output, err
	}
	return "", err
}

var appStopCmd = &cobra.Command{
	Use:   "stop [APPLICATION-ID]",
	Short: "Stops existing application",
	Long:  "Stops existing application",

	//For arguments take application names.
	//If any of the name is non-unique the command will be aborted.
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunAppStop(args)
		processOutput(output, err)
	},
}

func initAppStop() {
	appStopCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	AppsRootCmd.AddCommand(appStopCmd)
}

func RunAppStop(args []string) (string, error) {
	var (
		IDs []string
		err error
		ok  bool
		id  string
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingAppIdError
	}
	IDs, err = apps.StopAppID(id, asyncTask)

	if err != nil {
		return "", err
	} else if len(IDs) > 0 {
		var output string
		if asyncTask {
			output = "Application is being stopped: " + IDs[0]
		} else {
			output = "Application stopped: " + IDs[0]
		}
		return output, err
	}
	return "", err
}
