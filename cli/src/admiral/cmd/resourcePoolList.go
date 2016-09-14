package cmd

import (
	"fmt"

	"admiral/help"
	"admiral/resourcePools"

	"github.com/spf13/cobra"
)

func init() {
	rpListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	ResourcePoolsRootCmd.AddCommand(rpListCmd)
}

var rpListCmd = &cobra.Command{
	Use: "ls",

	Short: "Lists existing resource pools.",

	Long: "Lists existing resource pools.",

	Run: func(cmd *cobra.Command, args []string) {
		rpl := resourcePools.ResourcePoolList{}
		count := rpl.FetchRP()
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		rpl.Print()
	},
}
