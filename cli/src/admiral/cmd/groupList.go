package cmd

import (
	"admiral/groups"

	"github.com/spf13/cobra"
)

func init() {
	GroupsRootCmd.AddCommand(groupListCmd)
}

var groupListCmd = &cobra.Command{
	Use:   "ls",
	Short: "List groups.",
	Long:  "List groups.",

	Run: func(cmd *cobra.Command, args []string) {
		gl := &groups.GroupList{}
		gl.FetchGroups()
		gl.Print()
	},
}
