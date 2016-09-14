package cmd

import (
	"fmt"

	"admiral/apps"

	"github.com/spf13/cobra"
)

func init() {
	AppsRootCmd.AddCommand(appInspectCmd)
}

var appInspectCmd = &cobra.Command{
	Use:   "inspect [APPLICATION-ID]",
	Short: "Inspect application for additional info.",
	Long:  "Inspect application for additional info.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			id string
			ok bool
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter application ID.")
			return
		}
		apps.InspectID(id)
	},
}
