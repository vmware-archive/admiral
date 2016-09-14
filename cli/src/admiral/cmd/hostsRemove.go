package cmd

import (
	"fmt"

	"admiral/hosts"

	"admiral/functions"

	"github.com/spf13/cobra"
)

func init() {
	removeHostCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	HostsRootCmd.AddCommand(removeHostCmd)
}

var removeHostCmd = &cobra.Command{
	Use: "rm [HOST-ADDRESS]",

	Short: "Remove existing host.",

	Long: "Remove existing host.",

	//Main function for the "rm-host" command.
	//If any of the provided host doesn't exist, the command will be aborted.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			address string
			ok      bool
		)
		if address, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter host address.")
			return
		}
		fmt.Printf("Are you sure you want to remove %s? (y/n)\n", address)
		answer := functions.PromptAgreement()
		if answer == "n" || answer == "no" {
			fmt.Println("Remove command aborted!")
			return
		}

		newID, err := hosts.RemoveHost(address, asyncTask)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Host removed: " + newID)
		}
	},
}
