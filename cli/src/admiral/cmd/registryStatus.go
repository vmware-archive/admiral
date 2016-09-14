package cmd

import (
	"fmt"

	"admiral/registries"

	"github.com/spf13/cobra"
)

func init() {
	RegistriesRootCmd.AddCommand(regDisCmd)
	RegistriesRootCmd.AddCommand(regEnCmd)
}

var regDisCmd = &cobra.Command{
	Use:   "disable [REGISTRY-ID]",
	Short: "Disable registry.",
	Long:  "Disable registry.",
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
		newID, err = registries.DisableID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Registry disabled: " + newID)
		}
	},
}

var regEnCmd = &cobra.Command{
	Use:   "enable [REGISTRY-ID]",
	Short: "Enable registry.",
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
		newID, err = registries.EnableID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Registry enabled: " + newID)
		}
	},
}
