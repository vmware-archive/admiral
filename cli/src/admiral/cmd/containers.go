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
	"bufio"
	"errors"
	"fmt"
	"os"
	"strings"

	"admiral/containers"
	"admiral/functions"
	"admiral/help"

	"github.com/spf13/cobra"
)

var containerIdError = errors.New("Container ID not provided.")

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
		err := RunContainerExecute(args)
		if err != nil {
			fmt.Println(err)
		}
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

func RunContainerExecute(args []string) error {
	var (
		ok bool
		id string
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return containerIdError
	}

	if interact {
		interactive(id)
		return nil
	}
	containers.ExecuteCmd(id, execF)
	return nil
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
		output, err := RunContainerInspect(args)
		processOutput(output, err)
	},
}

func initContainerInspect() {
	RootCmd.AddCommand(containerInspectCmd)
}

func RunContainerInspect(args []string) (string, error) {
	var (
		ok bool
		id string
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", containerIdError
	}
	container := containers.InspectContainer(id)
	return container.StringJson(), nil
}

var containerRemoveCmd = &cobra.Command{
	Use: "rm [CONTAINER-ID]...",

	Short: "Remove existing container(s).",

	Long: "Remove existing container(s).",

	//Main function for "rm" command.
	//Args are the names of containers.
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunContainersRemove(args)
		processOutput(output, err)
	},
}

func initContainerRemove() {
	containerRemoveCmd.Flags().StringVarP(&queryF, "query", "q", "", "Every container that match the query will be removed.")
	containerRemoveCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	containerRemoveCmd.Flags().BoolVar(&autoAccept, "force", false, "Do not prompt asking for remove.")
	RootCmd.AddCommand(containerRemoveCmd)
}

func RunContainersRemove(args []string) (string, error) {
	var (
		resIDs []string
		err    error
	)
	if queryF != "" {
		resIDs, err = containers.RemoveMany(queryF, asyncTask)
	} else {
		if len(args) > 0 {
			if !autoAccept {
				fmt.Printf("Are you sure you want to remove %s? (y/n)\n", strings.Join(args, " "))
				answer := functions.PromptAgreement()
				if answer == "n" || answer == "no" {
					return "", errors.New("Remove command aborted.")
				}
				resIDs, err = containers.RemoveContainer(args, asyncTask)
			} else {
				resIDs, err = containers.RemoveContainer(args, asyncTask)
			}
		} else {
			return "", containerIdError
		}
	}

	if err != nil {
		return "", err
	} else {
		var output string
		if asyncTask {
			output = "Container(s) are being removed: " + strings.Join(resIDs, " ")
		} else {
			output = "Container(s) removed: " + strings.Join(resIDs, " ")
		}
		return output, err
	}
}

var containerRestartCmd = &cobra.Command{
	Use:   "restart [CONTAINER-ID]",
	Short: "Restart container.",
	Long:  "Restart container.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunContainerRestart(args)
		processOutput(output, err)
	},
}

func initContainerRestart() {
	containerRestartCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(containerRestartCmd)
}

func RunContainerRestart(args []string) (string, error) {
	var (
		resIDs []string
		err    error
	)
	if len(args) > 0 {
		resIDs, err = containers.StopContainer(args, asyncTask)
		resIDs, err = containers.StartContainer(args, asyncTask)
	} else {
		return "", containerIdError
	}

	if err != nil {
		return "", err
	} else {
		var output string
		if asyncTask {
			output = "Container(s) are being started: " + strings.Join(resIDs, " ")
		} else {
			output = "Container(s) started: " + strings.Join(resIDs, " ")
		}
		return output, err
	}
}

var containerScaleCmd = &cobra.Command{
	Use:   "scale [CONTAINER-ID]",
	Short: "Scale existing container",
	Long:  "Scale existing container",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunContainerScale(args)
		processOutput(output, err)
	},
}

func initContainerScale() {
	containerScaleCmd.Flags().Int32VarP(&scaleCount, "count", "c", 0, "(Required) Resource count.")
	containerScaleCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(containerScaleCmd)
}

func RunContainerScale(args []string) (string, error) {
	var (
		id    string
		ok    bool
		newID string
		err   error
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", containerIdError
	}

	if scaleCount < 1 {
		return "", errors.New("Scale count should be > 0.")
	}

	newID, err = containers.ScaleContainer(id, scaleCount, asyncTask)

	if err != nil {
		return "", err
	} else {
		var output string
		if asyncTask {
			output = "Container is being scaled: " + newID
		} else {
			output = "Container scaled: " + newID
		}
		return output, err
	}
}

var containerListCmd = &cobra.Command{
	Use:   "ps",
	Short: "Lists existing containers.",
	Long:  "Lists existing containers.",

	Run: func(cmd *cobra.Command, args []string) {
		RunContainerList(args)
	},
}

func initContainerList() {
	containerListCmd.Flags().BoolVarP(&allContainers, "all", "a", false, "Show all containers.")
	containerListCmd.Flags().StringVarP(&queryF, "query", "q", "", "Add query.")
	containerListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	RootCmd.AddCommand(containerListCmd)
}

func RunContainerList(args []string) {
	lc := &containers.ListContainers{}
	count := lc.FetchContainers(queryF)
	if count < 1 {
		fmt.Println("n/a")
		return
	}
	lc.Print(allContainers)
}

var containerStartCmd = &cobra.Command{
	Use:   "start [CONTAINER-ID]...",
	Short: "Starts existing container",
	Long:  "Starts existing container",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunContainerStart(args)
		processOutput(output, err)
	},
}

func initContainerStart() {
	containerStartCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(containerStartCmd)
}

func RunContainerStart(args []string) (string, error) {
	var (
		resIDs []string
		err    error
	)
	if len(args) > 0 {
		resIDs, err = containers.StartContainer(args, asyncTask)
	} else {
		return "", containerIdError
	}

	if err != nil {
		return "", err
	} else {
		var output string
		if asyncTask {
			output = "Container(s) are being started: " + strings.Join(resIDs, " ")
			return output, err
		} else {
			output = "Container(s) started: " + strings.Join(resIDs, " ")
			return output, err
		}
	}
}

var containerStopCmd = &cobra.Command{
	Use:   "stop [CONTAINER-ID]",
	Short: "Stops existing container",
	Long:  "Stops existing container",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunContainerStop(args)
		processOutput(output, err)
	},
}

func initContainerStop() {
	containerStopCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(containerStopCmd)
}

func RunContainerStop(args []string) (string, error) {
	var (
		resIDs []string
		err    error
	)

	if len(args) > 0 {
		resIDs, err = containers.StopContainer(args, asyncTask)
	} else {
		return "", containerIdError
	}

	if err != nil {
		return "", err
	} else {
		var output string
		if asyncTask {
			output = "Container(s) are being stopped: " + strings.Join(resIDs, " ")
			return output, err
		} else {
			output = "Container(s) stopped: " + strings.Join(resIDs, " ")
			return output, err
		}
	}
}

var containerRunCmd = &cobra.Command{
	Use:   "run [IMAGE]",
	Short: "Provision container",
	Long:  "Provision container",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunContainerRun(args)
		processOutput(output, err)
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

func RunContainerRun(args []string) (string, error) {
	var (
		imgName string
		ok      bool
		newID   string
		err     error
	)
	if imgName, ok = ValidateArgsCount(args); !ok {
		return "", errors.New("Image not provided.")
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
		return "", err
	} else {
		var output string
		if asyncTask {
			output = "\x1b[31;1mImage is being provisioned.\x1b[37;1m"
			return output, err
		} else {
			output = "Image provisioned: " + newID
			return output, err
		}
	}
}
