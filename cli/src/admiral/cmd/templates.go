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

	"errors"

	"github.com/spf13/cobra"
)

var (
	MissingTemplateIdError      = errors.New("Template ID not provided.")
	MissingPathToFileError      = errors.New("Path to file not provided.")
	FileFormatNotSpecifiedError = errors.New("File format is not specified.")
)

const (
	TemplateRemovedMessage        = "Template removed: "
	TemplateImportedMessage       = "Template imported: "
	TemplateExportedMessage       = "Template exported."
	TemplateClosureRemovedMessage = "Closure description removed: "
)

func init() {
	initTemplateList()
	initTemplateRemove()
	initTemplateImport()
	initTemplateExport()
	initTemplateInspect()

	initTemplateClosureList()
	initTemplateClosureRemove()
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
	//templateListCmd.Flags().BoolVarP(&inclCont, "containers", "c", false, inclContDesc) Currently disabled.
	templateListCmd.Flags().StringVarP(&queryF, "query", "q", "", queryFDesc)
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
	Use:   "rm [TEMPLATE]",
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
		return "", MissingTemplateIdError
	}
	newID, err = templates.RemoveTemplateID(id)

	if err != nil {
		return "", err
	} else {
		return TemplateRemovedMessage + newID, err
	}
}

var templateImportCmd = &cobra.Command{
	Use:   "import [PATH/TO/FILE]",
	Short: "Import yaml file.",
	Long:  "Import yaml file.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunTemplateImport(args)
		processOutput(output, err)
	},
}

var importKubernetes bool

func initTemplateImport() {
	templateImportCmd.Flags().BoolVar(&importKubernetes, "k8s", false, "Import k8s object.")
	templateImportCmd.Flags().MarkHidden("k8s")
	TemplatesRootCmd.AddCommand(templateImportCmd)
}

func RunTemplateImport(args []string) (string, error) {
	var (
		filePath, id string
		ok           bool
		err          error
	)
	if filePath, ok = ValidateArgsCount(args); !ok {
		return "", MissingPathToFileError
	}
	if importKubernetes {
		id, err = templates.ImportKubernetes(filePath)
	} else {
		id, err = templates.Import(filePath)
	}
	if err != nil {
		return "", err
	} else {
		return TemplateImportedMessage + id, err
	}
}

var templateExportCmd = &cobra.Command{
	Use:   "export [TEMPLATE]",
	Short: "Download exported application.",
	Long:  "Download exported application.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunTemplateExport(args)
		processOutput(output, err)
	},
}

func initTemplateExport() {
	templateExportCmd.Flags().StringVar(&formatTemplate, "format", "yaml", formatTemplateDesc)
	templateExportCmd.Flags().StringVar(&dirF, "file", "", required+"Path to the exported file.")
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
		return "", MissingTemplateIdError
	}
	newID, err := templates.Export(id, dirF, formatTemplate)
	if err != nil {
		return "", err
	} else {
		return TemplateExportedMessage + newID, err
	}
}

//verifyFormat verifies the given format in the flag.
//Returns true if format is valid, false if invalid.
func verifyFormat() (bool, error) {
	if formatTemplate != "yaml" && formatTemplate != "docker" {
		return false, FileFormatNotSpecifiedError
	}
	return true, nil
}

var templateInspectCmd = &cobra.Command{
	Use:   "inspect [TEMPLATE]",
	Short: "Inspect template.",
	Long:  "Inspect template.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunTemplateInspect(args)
		processOutput(output, err)
	},
}

func initTemplateInspect() {
	TemplatesRootCmd.AddCommand(templateInspectCmd)
}

func RunTemplateInspect(args []string) (string, error) {
	var (
		id string
		ok bool
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingTemplateIdError
	}
	return templates.InspectID(id)
}

//-------> Closure descriptions.

var templateClosureListCmd = &cobra.Command{
	Use:   "ls",
	Short: "List closure descriptions.",
	Long:  "List closure descriptions.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunTemplateClosureList(args)
		formatAndPrintOutput(output, err)
	},
}

func initTemplateClosureList() {
	TemplateClosureRootCmd.AddCommand(templateClosureListCmd)
}

func RunTemplateClosureList(args []string) (string, error) {
	cdl := &templates.ClosureDescriptionList{}
	_, err := cdl.FetchClosures()
	return cdl.GetOutputString(), err
}

var templateClosureRemoveCmd = &cobra.Command{
	Use:   "rm [CLOSURE-DESCRIPTION]",
	Short: "Remove closure description.",
	Long:  "Remove closure description.",

	Run: func(cmd *cobra.Command, args []string) {
		output, err := RunTemplateClosureRemove(args)
		processOutput(output, err)
	},
}

func initTemplateClosureRemove() {
	TemplateClosureRootCmd.AddCommand(templateClosureRemoveCmd)
}

func RunTemplateClosureRemove(args []string) (string, error) {
	var (
		id string
		ok bool
	)
	if id, ok = ValidateArgsCount(args); !ok {
		return "", MissingTemplateIdError
	}
	id, err := templates.RemoveClosureDescription(id)
	return TemplateClosureRemovedMessage + id, err
}
