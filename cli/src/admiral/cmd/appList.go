package cmd

import (
	"fmt"

	"admiral/apps"
	"admiral/help"

	"github.com/spf13/cobra"
)

func init() {
	listAppCmd.Flags().BoolVarP(&inclCont, "containers", "c", false, "Lists containers inside the template.")
	listAppCmd.Flags().StringVarP(&queryF, "query", "q", "", "Add query.")

	listAppCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	AppsRootCmd.AddCommand(listAppCmd)
}

var listAppCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing apps",
	Long:  "Lists existing applications.",

	//Main function for the "ls-app" command. It doesn't require any arguments.
	Run: func(cmd *cobra.Command, args []string) {
		la := apps.ListApps{}
		count := la.FetchApps(queryF)
		if count == 0 {
			fmt.Println("n/a")
			return
		}

		fmt.Println("Active Applications:")
		if inclCont {
			la.PrintActiveWithContainer()
		} else {
			la.PrintActiveWithoutContainer()
		}
	},
}
