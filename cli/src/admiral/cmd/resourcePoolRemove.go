package cmd

import (
	"fmt"

	"admiral/resourcePools"

	"github.com/spf13/cobra"
)

func init() {
	ResourcePoolsRootCmd.AddCommand(rpRemoveCmd)
}

var rpRemoveCmd = &cobra.Command{
	Use: "rm [RESOURCE-POOL-ID]",

	Short: "Removes existing resource pool.",

	Long: "Removes existing resource pool",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			err   error
			newID string
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter resource pool ID.")
			return
		}
		newID, err = resourcePools.RemoveRPID(id)

		if err == nil {
			fmt.Println("Resource pool removed: " + newID)
		} else if err != nil {
			fmt.Println(err)
		}
	},
}
