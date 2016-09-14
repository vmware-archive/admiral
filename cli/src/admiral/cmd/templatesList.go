package cmd

import (
	"fmt"

	"admiral/help"
	"admiral/templates"

	"github.com/spf13/cobra"
)

var imagesOnly bool

func init() {
	listTemplatesCmd.Flags().BoolVarP(&inclCont, "containers", "c", false, "Show all containers.")
	listTemplatesCmd.Flags().StringVarP(&queryF, "query", "q", "", "Add query.")
	listTemplatesCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	TemplatesRootCmd.AddCommand(listTemplatesCmd)
}

var listTemplatesCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing templates.",
	Long:  "Lists existing templates.",

	//Main function of the "ls-template" command.
	//It doesn't require any args but there are optional flags.
	//With -q or --query flag you can specify some keyword.
	//With -c or --containers flag you specify if the containers inside templates should be listed also.
	//with -i or --images flag you specify only images to be listed.
	//With -t or --templates flag you specify only templates to be lsited.
	Run: func(cmd *cobra.Command, args []string) {
		lt := &templates.TemplatesList{}
		count := lt.FetchTemplates(queryF)
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		if inclCont && !imagesOnly {
			lt.PrintWithContainer()
		} else if !imagesOnly {
			lt.PrintWithoutContainers()
		}
	},
}
