package cmd

import (
	"admiral/hosts"

	"fmt"

	"github.com/spf13/cobra"
)

func init() {
	HostsRootCmd.AddCommand(disblHostCmd)
}

var disblHostCmd = &cobra.Command{
	Use:   "disable [HOST-ADDRESS]",
	Short: "Disable host with address provided.",
	Long:  "Disable host with address provided.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			hostAddress string
			ok          bool
		)
		if hostAddress, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter host address.")
			return
		}
		newID, err := hosts.DisableHost(hostAddress)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Host disabled " + newID)
		}
	},
}
