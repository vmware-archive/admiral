package cmd

import (
	"fmt"

	"admiral/network"

	"github.com/spf13/cobra"
)

func init() {
	NetworksRootCmd.AddCommand(networkInspCmd)
}

var networkInspCmd = &cobra.Command{
	Use:   "inspect [NETWORK-NAME]",
	Short: "Display detailed network information",
	Long:  "Display detailed network information",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			name string
			ok   bool
		)
		if name, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter network.")
			return
		}
		found, str := network.InspectNetwork(name)
		if !found {
			fmt.Println("Network not found.")
			return
		}
		fmt.Println(str)
	},
}
