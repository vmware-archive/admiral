package cmd

import (
	"fmt"

	"admiral/groups"

	"github.com/spf13/cobra"
)

var groupDescription string

func init() {
	groupAddCmd.Flags().StringVar(&groupDescription, "description", "", "Group description.")
	GroupsRootCmd.AddCommand(groupAddCmd)
}

var groupAddCmd = &cobra.Command{
	Use:   "add [NAME]",
	Short: "Add group.",
	Long:  "Add group.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			name  string
			ok    bool
		)
		if name, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter group name.")
			return
		}
		newID, err = groups.AddGroup(name, groupDescription)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Print("Group added: " + newID)
		}
	},
}
