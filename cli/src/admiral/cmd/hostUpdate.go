package cmd

import (
	"fmt"

	"admiral/hosts"

	"github.com/spf13/cobra"
)

func init() {
	updateHostCmd.Flags().StringVar(&hostName, "name", "", "New host name.")
	updateHostCmd.Flags().StringVar(&credName, "credentials", "", "New credentials ID.")
	updateHostCmd.Flags().StringVar(&resPoolF, "resource-pool", "", "New resource pool ID.")
	updateHostCmd.Flags().StringVar(&deplPolicyF, "deployment-policy", "", "New deployment policy ID.")
	updateHostCmd.Flags().BoolVar(&autoAccept, "accept", false, "Auto accept if certificate is not trusted.")
	HostsRootCmd.AddCommand(updateHostCmd)
}

var updateHostCmd = &cobra.Command{
	Use:   "update [ADDRESS]",
	Short: "Edit existing hosts.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			address string
			ok      bool
		)
		if address, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter host address.")
			return
		}
		newID, err := hosts.EditHost(address, hostName, resPoolF, deplPolicyF, credName, autoAccept)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Host updated: " + newID)
		}
	},
}
