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

	"admiral/common/utils"
	"admiral/config"
	"admiral/containers"
	"admiral/help"

	"github.com/spf13/cobra"
)

var MissingContainerIdError = errors.New("Container ID not provided.")

const (
	ContainerStartedMessage        = "Container(s) started: "
	ContainerBeingStartedMessage   = "Container(s) is being started."
	ContainerStoppedMessage        = "Container(s) stoppped: "
	ContainerBeingStoppedMessage   = "Container(s) is being stopped."
	ContainerRestartedMessage      = "Container(s) restarted: "
	ContainerBeingRestartedMessage = "Container(s) is being restarted."
	ContainerRemovedMessage        = "Container(s) removed: "
	ContainerBeingRemovedMessage   = "Container(s) is being removed."
	ContainerProvisioned           = "Container provisioned: "
	ContainerBeingProvisioned      = "Container is being provisioned."
	ContainerScaledMessage         = "Container scaled: "
	ContainerBeingScaledMessage    = "Container is being scaled."
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
	Use:   "exec [CONTAINER]",
	Short: "Run a command in a running container.",
	Long:  "Run a command in a running container.",

	Run: func(cmd *cobra.Command, args []string) {
		err := RunContainerExecute(args)
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
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
		return MissingContainerIdError
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
	Use:   "inspect [CONTAINER]",
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
		return "", MissingContainerIdError
	}
	container, err := containers.InspectContainer(id)
	return string(container), err
}

var containerRemoveCmd = &cobra.Command{
	Use:   "rm [CONTAINER]...",
	Short: "Remove existing container(s).",
	Long:  "Remove existing container(s).",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunContainersRemove(args)
		processOutput(output, err)
	},
}

func initContainerRemove() {
	containerRemoveCmd.Flags().StringVarP(&queryF, "query", "q", "", "Every container that match the query will be removed.")
	containerRemoveCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	containerRemoveCmd.Flags().BoolVarP(&autoAccept, "force", "f", false, "Do not prompt asking for remove.")
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
				answer := utils.PromptAgreement()
				if !answer {
					return "", errors.New("Remove command aborted.")
				}
				resIDs, err = containers.RemoveContainer(args, asyncTask)
			} else {
				resIDs, err = containers.RemoveContainer(args, asyncTask)
			}
		} else {
			return "", MissingContainerIdError
		}
	}

	if err != nil {
		return "", err
	}
	if !asyncTask {
		return ContainerRemovedMessage + strings.Join(resIDs, " "), nil
	}
	return ContainerBeingRemovedMessage, nil
}

var containerRestartCmd = &cobra.Command{
	Use:   "restart [CONTAINER]",
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
		return "", MissingContainerIdError
	}

	if err != nil {
		return "", err
	}
	if !asyncTask {
		return ContainerRestartedMessage + strings.Join(resIDs, ", "), nil
	}
	return ContainerBeingRestartedMessage, nil
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
	containerScaleCmd.Flags().Int32VarP(&scaleCount, "count", "c", 0, required+scaleCountDesc)
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
		return "", MissingContainerIdError
	}

	if scaleCount < 1 {
		return "", errors.New("Cluster size should be greater than 0.")
	}

	newID, err = containers.ScaleContainer(id, scaleCount, asyncTask)

	if err != nil {
		return "", err
	}
	if !asyncTask {
		return ContainerScaledMessage + newID, nil
	}
	return ContainerBeingScaledMessage, nil
}

var containerListCmd = &cobra.Command{
	Use:   "ps",
	Short: "Lists existing containers.",
	Long:  "Lists existing containers.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunContainerList(args)
		formatAndPrintOutput(output, err)
	},
}

func initContainerList() {
	containerListCmd.Flags().BoolVarP(&allContainers, "all", "a", false, allContainersDesc)
	containerListCmd.Flags().StringVarP(&queryF, "query", "q", "", queryFDesc)
	containerListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	RootCmd.AddCommand(containerListCmd)
}

func RunContainerList(args []string) (string, error) {
	lc := &containers.ContainersList{}
	_, err := lc.FetchContainers(queryF)
	if err != nil {
		return "", err
	}
	return lc.GetOutputString(allContainers), nil
}

var containerStartCmd = &cobra.Command{
	Use:   "start [CONTAINER]...",
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
		return "", MissingContainerIdError
	}

	if err != nil {
		return "", err
	}
	if !asyncTask {
		return ContainerStartedMessage + strings.Join(resIDs, ", "), nil
	}
	return ContainerBeingStartedMessage, nil
}

var containerStopCmd = &cobra.Command{
	Use:   "stop [CONTAINER]",
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
		return "", MissingContainerIdError
	}

	if err != nil {
		return "", err
	}
	if !asyncTask {
		return ContainerStoppedMessage + strings.Join(resIDs, ", "), nil
	}
	return ContainerBeingStoppedMessage, nil
}

var containerRunCmd = &cobra.Command{
	Use:   "run [IMAGE]",
	Short: "Provision container",
	Long:  "Provision container",

	Run: func(cmd *cobra.Command, args []string) {
		output, errs := RunContainerRun(args)
		processOutputMultiErrors(output, errs)
	},
}

func initContainerRun() {
	containerRunCmd.Flags().IntVar(&customTimeout, "timeout", 0, customTimeoutDesc)
	containerRunCmd.Flags().StringVar(&cpuShares, "cpu-shares", "", cpuSharesDesc)
	containerRunCmd.Flags().Int32Var(&clusterSize, "cluster-size", 1, clusterSizeDesc)
	containerRunCmd.Flags().StringSliceVar(&cmds, "cmd", []string{}, cmdsDesc)
	containerRunCmd.Flags().StringSliceVarP(&envVariables, "env", "e", []string{}, envVariablesDesc)
	containerRunCmd.Flags().StringVarP(&hostName, "hostname", "h", "", hostNameDesc)
	containerRunCmd.Flags().StringVar(&logDriver, "log-driver", "", logDriverDesc)
	containerRunCmd.Flags().Int32Var(&retryCount, "max-restarts", 0, retryCountDesc)
	containerRunCmd.Flags().Int64VarP(&memoryLimit, "memory", "m", 0, memoryLimitDesc)
	containerRunCmd.Flags().Int64Var(&memorySwap, "memory-swap", 0, memoryLimitDesc)
	containerRunCmd.Flags().StringVar(&networkMode, "network-mode", "bridge", networkDriverDesc)
	containerRunCmd.Flags().StringVar(&containerName, "name", "", containerNameDesc)
	containerRunCmd.Flags().StringSliceVarP(&ports, "publish", "p", []string{}, portsDesc)
	containerRunCmd.Flags().BoolVarP(&publishAll, "publish-all", "P", true, publishAllDesc)
	containerRunCmd.Flags().StringVar(&restartPol, "restart", "no", restartPolDesc)
	containerRunCmd.Flags().StringVarP(&workingDir, "workdir", "w", "", workingDirDesc)
	containerRunCmd.Flags().StringSliceVarP(&volumes, "volume", "v", []string{}, volumesDesc)
	containerRunCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	if !utils.IsVraMode {
		containerRunCmd.Flags().StringVar(&projectF, "project", "", projectFDesc)
	} else {
		containerRunCmd.Flags().StringVar(&businessGroupId, "business-group", "", vraOptional+required+businessGroupIdDesc)
		containerRunCmd.Flags().StringVar(&deplPolicyF, "deployment-policy", "", deplPolicyFDesc)
	}
	containerRunCmd.Flags().Bool("help", false, "Help for "+RootCmd.Name())
	containerRunCmd.Flags().MarkHidden("help")
	RootCmd.AddCommand(containerRunCmd)
}

func RunContainerRun(args []string) (string, []error) {
	var (
		imageName string
		newID     string
		errorArr  []error
		ok        bool
	)

	if imageName, ok = ValidateArgsCount(args); !ok {
		err := errors.New("Image not provided.")
		return "", []error{err}
	}

	if customTimeout != 0 {
		config.TASK_TIMEOUT_SECONDS = customTimeout
	}

	cd := &containers.ContainerDescription{}

	err := cd.SetImage(imageName)
	errorArr = append(errorArr, err)

	err = cd.SetName(containerName)
	errorArr = append(errorArr, err)

	err = cd.SetNetworkMode(networkMode)
	errorArr = append(errorArr, err)

	err = cd.SetRestartPolicy(restartPol)
	errorArr = append(errorArr, err)

	err = cd.SetLogConfig(logDriver)
	errorArr = append(errorArr, err)

	err = cd.SetClusterSize(clusterSize)
	errorArr = append(errorArr, err)

	err = cd.SetMemoryLimit(memoryLimit)
	errorArr = append(errorArr, err)

	err = cd.SetMemorySwapLimit(memorySwap)
	errorArr = append(errorArr, err)

	cd.SetCommands(cmds)
	cd.SetVolumes(volumes)
	cd.SetPortBindings(ports)
	cd.SetEnvVars(envVariables)
	cd.SetPublishAll(publishAll)
	cd.SetCpuShares(cpuShares)
	cd.SetWorkingDir(workingDir)
	cd.SetHostName(hostName)
	cd.SetDeploymentPolicyId(deplPolicyF)
	cd.SetMaxRetryCount(retryCount)

	if !utils.IsVraMode {
		newID, err = cd.RunContainer(projectF, asyncTask)
		errorArr = append(errorArr, err)
	} else {
		newID, err = cd.RunContainer(businessGroupId, asyncTask)
		errorArr = append(errorArr, err)
	}

	if !asyncTask {
		return ContainerProvisioned + newID, errorArr
	}
	return ContainerBeingProvisioned, errorArr
}
