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

	"admiral/apps"
	"admiral/help"

	"admiral/utils"

	"strings"

	"admiral/config"
	"github.com/spf13/cobra"
)

var MissingAppIdError = errors.New("Application ID not provided.")

const (
	ApplicationStartedMessage        = "Application started: "
	ApplicationBeingStartedMessage   = "Application is being started."
	ApplicationStoppedMessage        = "Application stoppped: "
	ApplicationBeingStoppedMessage   = "Application is being stopped."
	ApplicationRestartedMessage      = "Application restarted: "
	ApplicationBeingRestartedMessage = "Application is being restarted."
	ApplicationRemovedMessage        = "Application removed: "
	ApplicationBeingRemovedMessage   = "Application is being removed."
	ApplicationProvisioned           = "Application provisioned: "
	ApplicationBeingProvisioned      = "Application is being provisioned."
)

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
	Use:   "inspect [APPLICATION]",
	Short: "Inspect application for additional info.",
	Long:  "Inspect application for additional info.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunAppInspect(args)
		processOutput(output, err)
	},
}

func initAppInspect() {
	AppsRootCmd.AddCommand(appInspectCmd)
}

func RunAppInspect(args []string) (string, error) {
	var (
		id string
		ok bool
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingAppIdError
	}
	return apps.InspectID(id)
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
	//appListCmd.Flags().BoolVarP(&inclCont, "containers", "c", false, inclContDesc) Currently disabled.
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
	Use:   "rm [APPLICATION]",
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

	if asyncTask {
		return ApplicationBeingRemovedMessage, err
	}
	return ApplicationRemovedMessage + strings.Join(IDs, ", "), err
}

var appRestartCmd = &cobra.Command{
	Use:   "restart [APPLICATION]",
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
	}
	if asyncTask {
		return ApplicationBeingRestartedMessage, nil
	}
	return ApplicationRestartedMessage + strings.Join(IDs, ", "), err
}

var appRunCmd = &cobra.Command{
	Use:   "run [TEMPLATE]",
	Short: "Provision application from template.",
	Long:  "Provision application from template.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunAppRun(args)
		processOutput(output, err)
	},
}

func initAppRun() {
	appRunCmd.Flags().IntVar(&customTimeout, "timeout", 0, customTimeoutDesc)
	appRunCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	appRunCmd.Flags().StringVar(&dirF, "file", "", "Provision template from file.")
	appRunCmd.Flags().BoolVar(&keepTemplate, "keep", false, keepTemplateDesc)
	if !utils.IsVraMode {
		appRunCmd.Flags().StringVar(&projectF, "project", "", projectFDesc)
	} else {
		appRunCmd.Flags().StringVar(&businessGroupId, "business-group", "", vraOptional+required+businessGroupIdDesc)
	}
	AppsRootCmd.AddCommand(appRunCmd)
}

func RunAppRun(args []string) (string, error) {
	var (
		IDs []string
		err error
		ok  bool
		id  string
	)

	if customTimeout != 0 {
		config.TASK_TIMEOUT = customTimeout
	}

	if !utils.IsVraMode {
		if dirF != "" {
			IDs, err = apps.RunAppFile(dirF, projectF, keepTemplate, asyncTask)
		} else {
			if id, ok = ValidateArgsCount(args); !ok {
				return "", MissingTemplateIdError
			}
			IDs, err = apps.RunAppID(id, projectF, asyncTask)
		}
	} else {
		if dirF != "" {
			IDs, err = apps.RunAppFile(dirF, businessGroupId, keepTemplate, asyncTask)
		} else {
			if id, ok = ValidateArgsCount(args); !ok {
				return "", MissingTemplateIdError
			}
			IDs, err = apps.RunAppID(id, businessGroupId, asyncTask)
		}
	}

	if err != nil {
		return "", err
	}
	if asyncTask {
		return ApplicationBeingProvisioned, nil
	}
	return ApplicationProvisioned + strings.Join(IDs, ", "), err
}

var appStartCmd = &cobra.Command{
	Use:   "start [APPLICATION]",
	Short: "Starts existing application",
	Long:  "Starts existing application",

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
	}
	if asyncTask {
		return ApplicationBeingStartedMessage, nil
	}
	return ApplicationStartedMessage + strings.Join(IDs, ", "), err
}

var appStopCmd = &cobra.Command{
	Use:   "stop [APPLICATION]",
	Short: "Stops existing application",
	Long:  "Stops existing application",

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
	}
	if asyncTask {
		return ApplicationBeingStoppedMessage, nil
	}
	return ApplicationStoppedMessage + strings.Join(IDs, ", "), err
}
