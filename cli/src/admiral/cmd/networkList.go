package cmd

import (
	"fmt"

	"admiral/help"
	"admiral/network"

	"github.com/spf13/cobra"
)

func init() {
	networkListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	NetworksRootCmd.AddCommand(networkListCmd)
}

var networkListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing networks.",
	Long:  "Lists existing networks.",
	Run: func(cmd *cobra.Command, args []string) {
		nl := network.NetworkList{}
		count := nl.FetchNetworks()
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		nl.Print()
	},
}
