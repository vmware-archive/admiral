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
	"admiral/help"
	"admiral/templates"
	"fmt"

	"admiral/apps"

	"github.com/spf13/cobra"
)

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
		lt := &templates.TemplatesList{}
		count := lt.FetchTemplates(queryF)
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		if inclCont {
			lt.PrintWithContainer()
		} else {
			lt.PrintWithoutContainers()
		}
	},
}

func initTemplateList() {
	templateListCmd.Flags().BoolVarP(&inclCont, "containers", "c", false, "Show all containers.")
	templateListCmd.Flags().StringVarP(&queryF, "query", "q", "", "Add query.")
	templateListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	TemplatesRootCmd.AddCommand(templateListCmd)
}

var templateRemoveCmd = &cobra.Command{
	Use:   "rm [TEMPLATE-ID]",
	Short: "Remove template.",
	Long:  "Remove template.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter template ID.")
			return
		}
		newID, err = templates.RemoveTemplateID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Template removed: " + newID)
		}
	},
}

func initTemplateRemove() {
	TemplatesRootCmd.AddCommand(templateRemoveCmd)
}

var templateImportCmd = &cobra.Command{
	Use:   "import [PATH/TO/FILE]",
	Short: "Import yaml file.",
	Long:  "Import yaml file.",

	//Main function for the command "import". No args are needed, just path to file after -f or --file flag.
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) < 1 {
			fmt.Println("Please enter existing file.")
			return
		}
		var (
			filePath string
			ok       bool
		)
		if filePath, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter path to file.")
			return
		}
		id, err := apps.Import(filePath)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Template imported: " + id)
		}
	},
}

func initTemplateImport() {
	TemplatesRootCmd.AddCommand(templateImportCmd)
}

var templateExportCmd = &cobra.Command{
	Use:   "export [TEMPLATE-ID]",
	Short: "Download exported application.",
	Long:  "Download exported application.",

	Run: func(cmd *cobra.Command, args []string) {
		if !verifyFormat() {
			return
		}
		var (
			id string
			ok bool
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter template ID.")
			return
		}
		newID, err := apps.Export(id, dirF, formatTemplate)
		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Template exported: " + newID)
		}
	},
}

func initTemplateExport() {
	templateExportCmd.Flags().StringVar(&formatTemplate, "format", "yaml", "(Required) File format - yaml/docker")
	templateExportCmd.Flags().StringVar(&dirF, "file", "", "(Required) path/to/file")
	TemplatesRootCmd.AddCommand(templateExportCmd)
}

//Function to verify the given template in the flag.
//Returns true if format is valid, false if invalid.
func verifyFormat() bool {
	if formatTemplate != "yaml" && formatTemplate != "docker" {
		fmt.Println("Choose either yaml or docker file format.")
		return false
	}
	return true
}
