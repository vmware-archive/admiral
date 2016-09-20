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
	"admiral/apps"
	"fmt"

	"admiral/help"

	"github.com/spf13/cobra"
)

var keepTemplate bool

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
		var (
			id string
			ok bool
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter application ID.")
			return
		}
		apps.InspectID(id)
	},
}

func initAppInspect() {
	AppsRootCmd.AddCommand(appInspectCmd)
}

var appListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing apps",
	Long:  "Lists existing applications.",

	//Main function for the "ls-app" command. It doesn't require any arguments.
	Run: func(cmd *cobra.Command, args []string) {
		la := apps.ListApps{}
		count := la.FetchApps(queryF)
		if count == 0 {
			fmt.Println("n/a")
			return
		}

		fmt.Println("Active Applications:")
		if inclCont {
			la.PrintActiveWithContainer()
		} else {
			la.PrintActiveWithoutContainer()
		}
	},
}

func initAppList() {
	appListCmd.Flags().BoolVarP(&inclCont, "containers", "c", false, "Lists containers inside the template.")
	appListCmd.Flags().StringVarP(&queryF, "query", "q", "", "Add query.")

	appListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	AppsRootCmd.AddCommand(appListCmd)
}

var appRemoveCmd = &cobra.Command{
	Use:   "rm [APPLICATION-ID]",
	Short: "Stops existing application",
	Long:  "Stops existing application",

	//Main function for the "rm-app" command.
	//For arguments take application names.
	//If any of the name is non-unique the command will be aborted.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			IDs []string
			err error
			ok  bool
			id  string
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter application ID.")
			return
		}
		IDs, err = apps.RemoveAppID(id, asyncTask)

		if err != nil {
			fmt.Println(err)
		} else if len(IDs) > 0 {
			if asyncTask {
				fmt.Println("Application is being removed: " + IDs[0])
			} else {
				fmt.Println("Application removed: " + IDs[0])
			}
		}
	},
}

func initAppRemove() {
	appRemoveCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	AppsRootCmd.AddCommand(appRemoveCmd)
}

var appRestartCmd = &cobra.Command{
	Use:   "restart [APPLICATION-ID]",
	Short: "Restarts application.",
	Long:  "Restarts application.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			IDs []string
			err error
			ok  bool
			id  string
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter application ID.")
			return
		}
		IDs, err = apps.StopAppID(id, asyncTask)
		IDs, err = apps.StartAppID(id, asyncTask)

		if err != nil {
			fmt.Println(err)
		} else if len(IDs) > 0 {
			if asyncTask {
				fmt.Println("Application is being restarted: " + IDs[0])
			} else {
				fmt.Println("Application restarted: " + IDs[0])
			}
		}
	},
}

func initAppRestart() {
	appRestartCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	AppsRootCmd.AddCommand(appRestartCmd)
}

var appRunCmd = &cobra.Command{
	Use:   "run [TEMPLATE-ID]",
	Short: "Provision application from template.",
	Long:  "Provision application from template.",

	Run: func(cmd *cobra.Command, args []string) {
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
				fmt.Println("Enter template ID.")
				return
			}
			IDs, err = apps.RunAppID(id, asyncTask)
		}

		if err != nil {
			fmt.Println(err)
		} else if len(IDs) > 0 {
			if asyncTask {
				fmt.Println("Application is provisioning: " + IDs[0])
			} else {
				fmt.Println("Application provisioned: " + IDs[0])
			}
		}
	},
}

func initAppRun() {
	appRunCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	appRunCmd.Flags().StringVar(&dirF, "file", "", "Provision template from file.")
	appRunCmd.Flags().BoolVar(&keepTemplate, "keep", false, "Do not remove template after provisioning.")
	appRunCmd.Flags().StringVar(&groupID, "group", "", "(Required) "+groupIDDesc)
	AppsRootCmd.AddCommand(appRunCmd)
}

var appStartCmd = &cobra.Command{
	Use:   "start [APPLICATION-ID]",
	Short: "Starts existing application",
	Long:  "Starts existing application",

	//Main function for "start-app" command.
	//For arguments take application names.
	//If any of the name is non-unique the command will be aborted.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			IDs []string
			err error
			ok  bool
			id  string
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter application ID.")
			return
		}
		IDs, err = apps.StartAppID(id, asyncTask)

		if err != nil {
			fmt.Println(err)
		} else if len(IDs) > 0 {
			if asyncTask {
				fmt.Println("Application is being started: " + IDs[0])
			} else {
				fmt.Println("Application started: " + IDs[0])
			}
		}
	},
}

func initAppStart() {
	appStartCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	AppsRootCmd.AddCommand(appStartCmd)
}

var appStopCmd = &cobra.Command{
	Use:   "stop [APPLICATION-ID]",
	Short: "Stops existing application",
	Long:  "Stops existing application",

	//For arguments take application names.
	//If any of the name is non-unique the command will be aborted.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			IDs []string
			err error
			ok  bool
			id  string
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter application ID.")
			return
		}
		IDs, err = apps.StopAppID(id, asyncTask)

		if err != nil {
			fmt.Println(err)
		} else if len(IDs) > 0 {
			if asyncTask {
				fmt.Println("Application is being stopped: " + IDs[0])
			} else {
				fmt.Println("Application stopped: " + IDs[0])
			}
		}
	},
}

func initAppStop() {
	appStopCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	AppsRootCmd.AddCommand(appStopCmd)
}
