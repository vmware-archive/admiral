package cmd

import (
	"admiral/hosts"

	"fmt"

	"github.com/spf13/cobra"
)

func init() {
	HostsRootCmd.AddCommand(enableHostCmd)
}

var enableHostCmd = &cobra.Command{
	Use:   "enable [HOST-ADDRESS]",
	Short: "Enable host with address provided.",
	Long:  "Enable host with address provided.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			hostAddress string
			ok          bool
		)
		if hostAddress, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter host address.")
			return
		}
		newID, err := hosts.EnableHost(hostAddress)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Host enabled: " + newID)
		}
	},
}
