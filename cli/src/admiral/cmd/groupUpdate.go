package cmd

import (
	"fmt"

	"admiral/groups"

	"github.com/spf13/cobra"
)

var (
	newDescription string
)

func init() {
	updateGroupCmd.Flags().StringVar(&newName, "name", "", "New name.")
	updateGroupCmd.Flags().StringVar(&newDescription, "description", "", "New description.")
	GroupsRootCmd.AddCommand(updateGroupCmd)
}

var updateGroupCmd = &cobra.Command{
	Use:   "update [GROUP-ID]",
	Short: "Update group.",
	Long:  "Update group.",

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
		newID, err = groups.EditGroupID(id, newName, newDescription)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Group updated: " + newID)
		}
	},
}
