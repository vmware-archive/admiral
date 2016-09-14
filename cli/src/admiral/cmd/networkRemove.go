package cmd

import (
	"fmt"

	"admiral/network"

	"github.com/spf13/cobra"
)

func init() {
	NetworksRootCmd.AddCommand(networkRmCmd)
}

var networkRmCmd = &cobra.Command{
	Use:   "rm [NETWORK-NAME]",
	Short: "Remove a network",
	Long:  "Remove a network",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			name string
			ok   bool
		)
		if name, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter network.")
			return
		}
		isRemoved := network.RemoveNetwork(name)
		if isRemoved {
			fmt.Println("Network removed successfully.")
		}
	},
}
