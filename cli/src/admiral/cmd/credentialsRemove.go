package cmd

import (
	"admiral/credentials"

	"fmt"

	"github.com/spf13/cobra"
)

func init() {
	CredentialsRootCmd.AddCommand(removeCredCmd)
}

var removeCredCmd = &cobra.Command{
	Use:   "rm [CREDENTIALS-ID]",
	Short: "Removes existing credentials.",
	Long:  "Removes existing credentials.",

	//Main function for the "rm-cred" command.
	//It takes credential name as args which.
	//If name is duplicate for other credentials, command is aborted.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			ok    bool
			id    string
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter credentials ID.")
			return
		}
		newID, err = credentials.RemoveCredentialsID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Credentials removed: " + newID)
		}
	},
}
