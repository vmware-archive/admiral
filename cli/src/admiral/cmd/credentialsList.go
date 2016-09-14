package cmd

import (
	"fmt"

	"admiral/credentials"
	"admiral/help"

	"github.com/spf13/cobra"
)

func init() {
	listCredCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	CredentialsRootCmd.AddCommand(listCredCmd)
}

var listCredCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists credentials.",
	Long:  "Lists credentials.",

	//Main function for the "ls-cred" command.
	//It doesn't require any arguments.
	Run: func(cmd *cobra.Command, args []string) {
		lc := &credentials.ListCredentials{}
		count := lc.FetchCredentials()
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		lc.Print()
	},
}
