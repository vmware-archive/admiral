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
	"admiral/help"
	"admiral/templates"

	"errors"

	"github.com/spf13/cobra"
)

var templateIdError = errors.New("Template ID not provided.")

func init() {
	initTemplateList()
	initTemplateRemove()
	initTemplateImport()
	initTemplateExport()
}

var templateListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing templates.",
	Long:  "Lists existing templates.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunTemplatesList(args)
		formatAndPrintOutput(output, err)
	},
}

func initTemplateList() {
	templateListCmd.Flags().BoolVarP(&inclCont, "containers", "c", false, "Show all containers.")
	templateListCmd.Flags().StringVarP(&queryF, "query", "q", "", "Add query.")
	templateListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	TemplatesRootCmd.AddCommand(templateListCmd)
}

func RunTemplatesList(args []string) (string, error) {
	lt := &templates.TemplatesList{}
	_, err := lt.FetchTemplates(queryF)
	if inclCont {
		if err != nil {
			return "", err
		}
		return lt.GetOutputStringWithContainers()
	} else {
		return lt.GetOutputStringWithoutContainers(), err
	}
}

var templateRemoveCmd = &cobra.Command{
	Use:   "rm [TEMPLATE-ID]",
	Short: "Remove template.",
	Long:  "Remove template.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunTemplateRemove(args)
		processOutput(output, err)
	},
}

func initTemplateRemove() {
	TemplatesRootCmd.AddCommand(templateRemoveCmd)
}

func RunTemplateRemove(args []string) (string, error) {
	var (
		newID string
		err   error
		id    string
		ok    bool
	)

	if id, ok = ValidateArgsCount(args); !ok {
		return "", templateIdError
	}
	newID, err = templates.RemoveTemplateID(id)

	if err != nil {
		return "", err
	} else {
		return "Template removed: " + newID, err
	}
}

var templateImportCmd = &cobra.Command{
	Use:   "import [PATH/TO/FILE]",
	Short: "Import yaml file.",
	Long:  "Import yaml file.",

	//Main function for the command "import". No args are needed, just path to file after -f or --file flag.
	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunTemplateImport(args)
		processOutput(output, err)
	},
}

func initTemplateImport() {
	TemplatesRootCmd.AddCommand(templateImportCmd)
}

func RunTemplateImport(args []string) (string, error) {
	var (
		filePath string
		ok       bool
	)
	if filePath, ok = ValidateArgsCount(args); !ok {
		return "", errors.New("Path to file not provided.")
	}
	id, err := apps.Import(filePath)

	if err != nil {
		return "", err
	} else {
		return "Template imported: " + id, err
	}
}

var templateExportCmd = &cobra.Command{
	Use:   "export [TEMPLATE-ID]",
	Short: "Download exported application.",
	Long:  "Download exported application.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunTemplateExport(args)
		processOutput(output, err)
	},
}

func initTemplateExport() {
	templateExportCmd.Flags().StringVar(&formatTemplate, "format", "yaml", "(Required) File format - yaml/docker")
	templateExportCmd.Flags().StringVar(&dirF, "file", "", "(Required) path/to/file")
	TemplatesRootCmd.AddCommand(templateExportCmd)
}

func RunTemplateExport(args []string) (string, error) {
	if ok, err := verifyFormat(); !ok {
		return "", err
	}
	var (
		id string
		ok bool
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", templateIdError
	}
	newID, err := apps.Export(id, dirF, formatTemplate)
	if err != nil {
		return "", err
	} else {
		return "Template exported: " + newID, err
	}
}

//Function to verify the given template in the flag.
//Returns true if format is valid, false if invalid.
func verifyFormat() (bool, error) {
	if formatTemplate != "yaml" && formatTemplate != "docker" {
		return false, errors.New("Choose either yaml or docker file format.")
	}
	return true, nil
}
