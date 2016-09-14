package cmd

import (
	"admiral/registries"

	"fmt"

	"github.com/spf13/cobra"
)

var (
	newAddress string
	newCred    string
)

func init() {
	updateRegistryCmd.Flags().StringVar(&newAddress, "address", "", "New address of registry.")
	updateRegistryCmd.Flags().StringVar(&newCred, "credentials", "", "New credentials name.")
	updateRegistryCmd.Flags().StringVar(&newName, "name", "", "New registry name.")
	updateRegistryCmd.Flags().BoolVar(&autoAccept, "accept", false, "Auto accept if certificate is not trusted.")
	RegistriesRootCmd.AddCommand(updateRegistryCmd)
}

var updateRegistryCmd = &cobra.Command{
	Use:   "update [REGISTRY-ID]",
	Short: "",
	Long:  "",

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
		newID, err = registries.EditRegistryID(id, newAddress, newName, newCred, autoAccept)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Registry updated: " + newID)
		}
	},
}
