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

	"github.com/spf13/cobra"
)

func init() {
	RootCmd.AddCommand(printHelpCmd)
}

var printHelpCmd = &cobra.Command{
	Use:    "wiki",
	Hidden: true,
	Run: func(cmd *cobra.Command, args []string) {
		generateContentTable()
		fmt.Println()
		generateBody()
	},
}

func generateBody() {
	forContainers, rest := distinguishCommands(RootCmd)

	//Iterate administrative commands
	fmt.Println("## admiral _(basic)_")
	for _, cmd := range rest {
		if !cmd.Hidden && len(cmd.Commands()) < 1 && !IsHelpCmd(cmd) {
			fmt.Println("#### " + cmd.CommandPath())
			fmt.Println("```")
			cmd.Help()
			fmt.Println("```")
			fmt.Println()
		}
	}

	//Iterate containers related commands
	fmt.Println("## admiral _(container management)_")
	for _, cmd := range forContainers {
		if !cmd.Hidden && len(cmd.Commands()) < 1 && !IsHelpCmd(cmd) {
			fmt.Println("#### " + cmd.CommandPath())
			fmt.Println("```")
			cmd.Help()
			fmt.Println("```")
			fmt.Println()
		}
	}

	//Iterate all commands which have subcommands.
	for _, cmd := range RootCmd.Commands() {
		if len(cmd.Commands()) > 1 && !cmd.Hidden {
			fmt.Println("## " + cmd.CommandPath())
			//Iterate the subcommands.
			for _, childCmd := range cmd.Commands() {
				fmt.Println("#### " + childCmd.CommandPath())
				fmt.Println("```")
				childCmd.Help()
				fmt.Println("```")
				fmt.Println()
			}
		}
	}
}

func generateContentTable() {
	forContainers, rest := distinguishCommands(RootCmd)
	fmt.Println("# Admiral CLI commands")

	//Iterate administrative commands
	fmt.Println("### [admiral _(basic)_] (#admiral-basic-1)   ")
	for _, cmd := range rest {
		if !cmd.Hidden && len(cmd.Commands()) < 1 && !IsHelpCmd(cmd) {
			fmt.Println("   " + getLinkLastCommand(cmd) + "   ")
		}
	}

	//Iterate containers related commands
	fmt.Println("### [admiral _(container management)_] (#admiral-container-management-1)   ")
	for _, cmd := range forContainers {
		if !cmd.Hidden && len(cmd.Commands()) < 1 && !IsHelpCmd(cmd) {
			fmt.Println("  " + getLinkLastCommand(cmd) + "   ")
		}
	}

	fmt.Println()

	//Iterate all commands which have subcommands.
	for _, cmd := range RootCmd.Commands() {
		if len(cmd.Commands()) > 1 && !cmd.Hidden {
			fmt.Println("### " + getCmdLink(cmd) + "   ")
			//Iterate the subcommands.
			for _, childCmd := range cmd.Commands() {
				fmt.Println("   " + getLinkLastCommand(childCmd) + "   ")
			}

		}
	}

}

func IsHelpCmd(cmd *cobra.Command) bool {
	if strings.Contains(cmd.CommandPath(), "help") {
		return true
	}
	return false
}

func distinguishCommands(rootCmd *cobra.Command) ([]*cobra.Command, []*cobra.Command) {
	forContainers := make([]*cobra.Command, 0)
	rest := make([]*cobra.Command, 0)

	for _, cmd := range rootCmd.Commands() {
		if strings.Contains(cmd.Short, "container") {
			forContainers = append(forContainers, cmd)
		} else {
			rest = append(rest, cmd)
		}
	}
	return forContainers, rest
}

func getLinkLastCommand(cmd *cobra.Command) string {
	cmds := strings.Split(cmd.CommandPath(), " ")
	return fmt.Sprintf("[%s] (%s)", cmds[len(cmds)-1], "#"+strings.Join(cmds, "-"))
}

func getCmdLink(cmd *cobra.Command) string {
	cmds := strings.Split(cmd.CommandPath(), " ")
	return fmt.Sprintf("[%s] (%s-1)", cmd.CommandPath(), "#"+strings.Join(cmds, "-"))
}
