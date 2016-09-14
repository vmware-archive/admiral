package cmd

import (
	"fmt"

	"admiral/help"
	"admiral/hosts"

	"github.com/spf13/cobra"
)

func init() {
	listHostsCmd.Flags().StringVarP(&queryF, "query", "q", "", "Add query.")
	listHostsCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	HostsRootCmd.AddCommand(listHostsCmd)
}

var listHostsCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing hosts.",
	Long:  "Lists existing hosts.",

	//Main function for the "ls-host" command.
	//It doesn't require any args, but there is optional -q or --query flag,
	//after which you can provide specific keyword to look for.
	Run: func(cmd *cobra.Command, args []string) {
		hl := &hosts.HostsList{}
		count := hl.FetchHosts(queryF)
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		hl.Print()
	},
}
