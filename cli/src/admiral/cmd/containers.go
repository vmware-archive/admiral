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
	"admiral/containers"
	"bufio"
	"fmt"
	"os"
	"strings"

	"admiral/functions"

	"admiral/help"

	"github.com/spf13/cobra"
)

func init() {
	initContainerExec()
	initContainerInspect()
	initContainerRemove()
	initContainerRestart()
	initContainerScale()
	initContainerList()
	initContainerStart()
	initContainerStop()
	initContainerRun()
}

var containerExecCmd = &cobra.Command{
	Use:   "exec [CONTAINER-ID]",
	Short: "Run a command in a running container.",
	Long:  "Run a command in a running container.",

	Run: func(cmd *cobra.Command, args []string) {
		if len(args) < 1 {
			fmt.Println("Enter container ID.")
			return
		}

		var (
			ok bool
			id string
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter container.")
			return
		}

		if interact {
			interactive(id)
			return
		}
		containers.ExecuteCmd(id, execF)
	},
}

var (
	execF    string
	interact bool
)

func initContainerExec() {
	containerExecCmd.Flags().StringVar(&execF, "cmd", "", "Command to execute.")
	containerExecCmd.Flags().BoolVarP(&interact, "interactive", "i", false, "Interactive mode.")
	RootCmd.AddCommand(containerExecCmd)
}

func interactive(id string) {
	reader := bufio.NewReader(os.Stdin)
	var input string
	fmt.Print(">")
	input, _ = reader.ReadString('\n')
	for {
		if strings.TrimSpace(input) == "exit" {
			break
		}
		containers.ExecuteCmd(id, strings.TrimSpace(input))
		fmt.Print(">")
		input, _ = reader.ReadString('\n')
	}
}

var containerInspectCmd = &cobra.Command{
	Use:   "inspect [CONTAINER-ID]",
	Short: "Return low-level information on a container.",
	Long:  "Return low-level information on a container.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			ok bool
			id string
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter container.")
			return
		}
		container := containers.InspectContainer(id)
		fmt.Println(container.StringJson())
	},
}

func initContainerInspect() {
	RootCmd.AddCommand(containerInspectCmd)
}

var containerRemoveCmd = &cobra.Command{
	Use: "rm [CONTAINER-ID]...",

	Short: "Remove existing container(s).",

	Long: "Remove existing container(s).",

	//Main function for "rm" command.
	//Args are the names of containers.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			resIDs []string
			err    error
		)
		if queryF != "" {
			resIDs, err = containers.RemoveMany(queryF, asyncTask)
		} else {
			if len(args) > 0 {
				fmt.Printf("Are you sure you want to remove %s? (y/n)\n", strings.Join(args, " "))
				answer := functions.PromptAgreement()
				if answer == "n" || answer == "no" {
					fmt.Println("Remove command aborted!")
					return
				}
				resIDs, err = containers.RemoveContainer(args, asyncTask)
			} else {
				fmt.Println("Enter container(s) ID.")
				return
			}
		}

		if err != nil {
			fmt.Println(err)
		} else {
			if asyncTask {
				fmt.Println("Container(s) are being removed: " + strings.Join(resIDs, " "))
			} else {
				fmt.Println("Container(s) removed: " + strings.Join(resIDs, " "))
			}
		}
	},
}

func initContainerRemove() {
	containerRemoveCmd.Flags().StringVarP(&queryF, "query", "q", "", "Every container that match the query will be removed.")
	containerRemoveCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(containerRemoveCmd)
}

var containerRestartCmd = &cobra.Command{
	Use:   "restart [CONTAINER-ID]",
	Short: "Restart container.",
	Long:  "Restart container.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			resIDs []string
			err    error
		)
		if len(args) > 0 {
			resIDs, err = containers.StopContainer(args, asyncTask)
			resIDs, err = containers.StartContainer(args, asyncTask)
		} else {
			fmt.Println("Enter container(s) ID.")
			return
		}

		if err != nil {
			fmt.Println(err)
		} else {
			if asyncTask {
				fmt.Println("Container(s) are being started: " + strings.Join(resIDs, " "))
			} else {
				fmt.Println("Container(s) started: " + strings.Join(resIDs, " "))
			}
		}

	},
}

func initContainerRestart() {
	containerRestartCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(containerRestartCmd)
}

var containerScaleCmd = &cobra.Command{
	Use:   "scale [CONTAINER-ID]",
	Short: "Scale existing container",
	Long:  "Scale existing container",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			id    string
			ok    bool
			newID string
			err   error
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter container.")
			return
		}

		if scaleCount < 1 {
			fmt.Println("Please provide scale count > 0")
			return
		}

		newID, err = containers.ScaleContainer(id, scaleCount, asyncTask)

		if err != nil {
			fmt.Println(err)
		} else {
			if asyncTask {
				fmt.Println("Container is being scaled: " + newID)
			} else {
				fmt.Println("Container scaled: " + newID)
			}
		}

	},
}

func initContainerScale() {
	containerScaleCmd.Flags().Int32VarP(&scaleCount, "count", "c", 0, "(Required) Resource count.")
	containerScaleCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(containerScaleCmd)
}

var containerListCmd = &cobra.Command{
	Use:   "ps",
	Short: "Lists existing containers.",
	Long:  "Lists existing containers.",

	//Main function for "ls" command. It doesn't require any arguments.
	Run: func(cmd *cobra.Command, args []string) {
		lc := &containers.ListContainers{}
		count := lc.FetchContainers(queryF)
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		lc.Print(allContainers)
	},
}

func initContainerList() {
	containerListCmd.Flags().BoolVarP(&allContainers, "all", "a", false, "Show all containers.")
	containerListCmd.Flags().StringVarP(&queryF, "query", "q", "", "Add query.")
	containerListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	RootCmd.AddCommand(containerListCmd)
}

var containerStartCmd = &cobra.Command{
	Use:   "start [CONTAINER-ID]...",
	Short: "Starts existing container",
	Long:  "Starts existing container",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			resIDs []string
			err    error
		)
		if len(args) > 0 {
			resIDs, err = containers.StartContainer(args, asyncTask)
		} else {
			fmt.Println("Enter container(s) ID.")
			return
		}

		if err != nil {
			fmt.Println(err)
		} else {
			if asyncTask {
				fmt.Println("Container(s) are being started: " + strings.Join(resIDs, " "))
			} else {
				fmt.Println("Container(s) started: " + strings.Join(resIDs, " "))
			}
		}
	},
}

func initContainerStart() {
	containerStartCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(containerStartCmd)
}

var containerStopCmd = &cobra.Command{
	Use:   "stop [CONTAINER-ID]",
	Short: "Stops existing container",
	Long:  "Stops existing container",
	//Main function to stop existing container by provided name.
	//At this state it will try to stop all of the given containers, without check their current state or if non-unique names are given.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			resIDs []string
			err    error
		)

		if len(args) > 0 {
			resIDs, err = containers.StopContainer(args, asyncTask)
		} else {
			fmt.Println("Enter container(s) ID.")
			return
		}

		if err != nil {
			fmt.Println(err)
		} else {
			if asyncTask {
				fmt.Println("Container(s) are being stopped: " + strings.Join(resIDs, " "))
			} else {
				fmt.Println("Container(s) stopped: " + strings.Join(resIDs, " "))
			}
		}

	},
}

func initContainerStop() {
	containerStopCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(containerStopCmd)
}

var containerRunCmd = &cobra.Command{
	Use:   "run [IMAGE]",
	Short: "Provision container",
	Long:  "Provision container",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			imgName string
			ok      bool
			newID   string
			err     error
		)
		if imgName, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter image.")
			return
		}
		imgNameArr := strings.Split(imgName, "/")
		name := imgNameArr[len(imgNameArr)-1]

		cd := &containers.ContainerDescription{}
		cd.Create(
			imgName, name, cpuShares, networkMode, restartPol, workingDir, logDriver, hostName, deplPolicyF, //strings
			clusterSize, retryCount, //int32
			memory, memorySwap, //int64
			cmds, env, volumes, ports, //[]string
			publishAll) //bool
		newID, err = cd.RunContainer(asyncTask)
		if err != nil {
			fmt.Println(err)
		} else {
			if asyncTask {
				fmt.Println("\x1b[31;1mImage is being provisioned.\x1b[37;1m")
			} else {
				fmt.Println("Image is provisioned: " + newID)
			}
		}
	},
}

func initContainerRun() {
	containerRunCmd.Flags().StringVar(&cpuShares, "cpu-shares", "", "An integer value containing the container CPU shares")
	containerRunCmd.Flags().Int32Var(&clusterSize, "cluster-size", 1, "The number of nodes to be provisioned.")
	containerRunCmd.Flags().StringSliceVar(&cmds, "cmd", []string{}, "Commands to run on container start.")
	containerRunCmd.Flags().StringVar(&deplPolicyF, "deployment-policy", "", "Deployment policy name.")
	containerRunCmd.Flags().StringSliceVarP(&env, "env", "e", []string{}, "Set enivornment variables.")
	containerRunCmd.Flags().StringVarP(&hostName, "hostname", "h", "", "Container host name.")
	containerRunCmd.Flags().StringVar(&logDriver, "log-driver", "", "Logging driver for container.")
	containerRunCmd.Flags().Int32Var(&retryCount, "max-restarts", 0, "Max restart count on container failures.")
	containerRunCmd.Flags().Int64VarP(&memory, "memory", "m", 0, "Memory limit")
	containerRunCmd.Flags().Int64Var(&memorySwap, "memory-swap", 0, "Total memory limit(Memory + Swap), set -1 to disable swap")
	containerRunCmd.Flags().StringVar(&networkMode, "network-mode", "bridge", "Sets the networking mode for the container.")
	containerRunCmd.Flags().StringSliceVarP(&ports, "publish", "p", []string{}, "Publish a container's port(s) to the host.")
	containerRunCmd.Flags().BoolVarP(&publishAll, "publish-all", "P", true, "Publish all exposed ports to random ports.")
	containerRunCmd.Flags().StringVar(&restartPol, "restart", "no", "Restart policy to apply.")
	containerRunCmd.Flags().StringVarP(&workingDir, "workdir", "w", "", "Working directory inside the container")
	containerRunCmd.Flags().StringSliceVarP(&volumes, "volume", "v", []string{}, "Bind mount volume")
	containerRunCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	containerRunCmd.Flags().Bool("help", false, "Help for "+RootCmd.Name())
	containerRunCmd.Flags().MarkHidden("help")
	RootCmd.AddCommand(containerRunCmd)
}
