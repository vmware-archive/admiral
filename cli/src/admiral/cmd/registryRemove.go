package cmd

import (
	"fmt"

	"admiral/registries"

	"github.com/spf13/cobra"
)

func init() {
	RegistriesRootCmd.AddCommand(regRemoveCmd)
}

var regRemoveCmd = &cobra.Command{
	Use:   "rm [REGISTRY-ID]",
	Short: "Remove existing registry.",
	Long:  "Remove existing registry.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter registry ID.")
			return
		}
		newID, err = registries.RemoveRegistryID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Registry removed: " + newID)
		}

	},
}
