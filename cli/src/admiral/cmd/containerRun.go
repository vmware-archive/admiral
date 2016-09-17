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
	"strings"

	"admiral/containers"

	"github.com/spf13/cobra"
)

var (
	clusterSize int32
	cmds        []string
	env         []string
	hostName    string
	retryCount  int32
	logDriver   string
	memory      int64
	memorySwap  int64
	networkMode string
	ports       []string
	publishAll  bool
	restartPol  string
	workingDir  string
	volumes     []string
)

func init() {
	runContainerCmd.Flags().StringVar(&cpuShares, "cpu-shares", "", "An integer value containing the container CPU shares")

	runContainerCmd.Flags().Int32Var(&clusterSize, "cluster-size", 1, "The number of nodes to be provisioned.")

	runContainerCmd.Flags().StringSliceVar(&cmds, "cmd", []string{}, "Commands to run on container start.")

	runContainerCmd.Flags().StringVar(&deplPolicyF, "deployment-policy", "", "Deployment policy name.")

	runContainerCmd.Flags().StringSliceVarP(&env, "env", "e", []string{}, "Set enivornment variables.")

	runContainerCmd.Flags().StringVarP(&hostName, "hostname", "h", "", "Container host name.")

	runContainerCmd.Flags().StringVar(&logDriver, "log-driver", "", "Logging driver for container.")

	runContainerCmd.Flags().Int32Var(&retryCount, "max-restarts", 0, "Max restart count on container failures.")

	runContainerCmd.Flags().Int64VarP(&memory, "memory", "m", 0, "Memory limit")

	runContainerCmd.Flags().Int64Var(&memorySwap, "memory-swap", 0, "Total memory limit(Memory + Swap), set -1 to disable swap")

	runContainerCmd.Flags().StringVar(&networkMode, "network-mode", "bridge", "Sets the networking mode for the container.")

	runContainerCmd.Flags().StringSliceVarP(&ports, "publish", "p", []string{}, "Publish a container's port(s) to the host.")

	runContainerCmd.Flags().BoolVarP(&publishAll, "publish-all", "P", true, "Publish all exposed ports to random ports.")

	runContainerCmd.Flags().StringVar(&restartPol, "restart", "no", "Restart policy to apply.")

	runContainerCmd.Flags().StringVarP(&workingDir, "workdir", "w", "", "Working directory inside the container")

	runContainerCmd.Flags().StringSliceVarP(&volumes, "volume", "v", []string{}, "Bind mount volume")

	runContainerCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)

	runContainerCmd.Flags().Bool("help", false, "Help for "+RootCmd.Name())
	runContainerCmd.Flags().MarkHidden("help")
	RootCmd.AddCommand(runContainerCmd)
}

var runContainerCmd = &cobra.Command{
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
