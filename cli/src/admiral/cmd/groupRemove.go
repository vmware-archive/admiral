package cmd

import (
	"fmt"

	"admiral/groups"

	"github.com/spf13/cobra"
)

func init() {
	GroupsRootCmd.AddCommand(removeGroupCmd)
}

var removeGroupCmd = &cobra.Command{
	Use:   "rm [GROUP-ID]",
	Short: "Remove group.",
	Long:  "Remove group.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter group ID.")
			return
		}
		newID, err = groups.RemoveGroupID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Group removed: " + newID)
		}

	},
}
