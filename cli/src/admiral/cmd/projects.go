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

	"admiral/projects"

	"github.com/spf13/cobra"
)

var MissingProjectIdError = errors.New("Project ID not provided.")

func init() {
	initProjectAdd()
	initProjectList()
	initProjectRemove()
	initProjectUpdate()
}

var projectAddCmd = &cobra.Command{
	Use:   "add [NAME]",
	Short: "Add project.",
	Long:  "Add project.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunProjectAdd(args)
		processOutput(output, err)
	},
}

func initProjectAdd() {
	projectAddCmd.Flags().StringVar(&projectDescription, "description", "", "Project description.")
	ProjectsRootCmd.AddCommand(projectAddCmd)
}

func RunProjectAdd(args []string) (string, error) {
	var (
		newID string
		err   error
		name  string
		ok    bool
	)
	if name, ok = ValidateArgsCount(args); !ok {
		return "", errors.New("Project name not provided.")
	}
	newID, err = projects.AddProject(name, projectDescription)

	if err != nil {
		return "", err
	} else {
		return "Project added: " + newID, err
	}
}

var projectListCmd = &cobra.Command{
	Use:   "ls",
	Short: "List projects.",
	Long:  "List projects.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunProjectList(args)
		formatAndPrintOutput(output, err)
	},
}

func initProjectList() {
	ProjectsRootCmd.AddCommand(projectListCmd)
}

func RunProjectList(args []string) (string, error) {
	gl := &projects.ProjectList{}
	_, err := gl.FetchProjects()
	return gl.GetOutputString(), err
}

var projectRemoveCmd = &cobra.Command{
	Use:   "rm [GROUP-ID]",
	Short: "Remove project.",
	Long:  "Remove project.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunProjectRemove(args)
		processOutput(output, err)
	},
}

func initProjectRemove() {
	ProjectsRootCmd.AddCommand(projectRemoveCmd)
}

func RunProjectRemove(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingProjectIdError
	}
	newID, err = projects.RemoveProjectID(id)

	if err != nil {
		return "", err
	} else {
		return "Project removed: " + newID, err
	}
}

var projectUpdateCmd = &cobra.Command{
	Use:   "update [project-ID]",
	Short: "Update project.",
	Long:  "Update project.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunProjectUpdate(args)
		processOutput(output, err)
	},
}

func initProjectUpdate() {
	projectUpdateCmd.Flags().StringVar(&newName, "name", "", "New name.")
	projectUpdateCmd.Flags().StringVar(&newDescription, "description", "", "New description.")
	ProjectsRootCmd.AddCommand(projectUpdateCmd)
}

func RunProjectUpdate(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingProjectIdError
	}
	newID, err = projects.EditProjectID(id, newName, newDescription)

	if err != nil {
		return "", err
	} else {
		return "Project updated: " + newID, err
	}
}
