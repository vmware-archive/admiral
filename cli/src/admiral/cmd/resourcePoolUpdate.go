package cmd

import (
	"fmt"
	"admiral/resourcePools"

	"github.com/spf13/cobra"
)

func init() {
	rpUpdateCmd.Flags().StringVar(&newName, "name", "", "New name of resource pool.")
	ResourcePoolsRootCmd.AddCommand(rpUpdateCmd)
}

var rpUpdateCmd = &cobra.Command{
	Use:   "update [RESOURCE-POOL-ID]",
	Short: "Edit resource pool",
	Long:  "Edit resource pool",
	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter resource pool.")
			return
		}
		newID, err = resourcePools.EditRPID(id, newName)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Resource pool updated: " + newID)
		}
	},
}
