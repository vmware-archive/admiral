package cmd

import (
	"admiral/credentials"

	"fmt"

	"github.com/spf13/cobra"
)

func init() {
	//Flag after which you have to specify the location of your public key.
	credUpdateCmd.Flags().StringVar(&publicCert, "public", "", "Location to new public key.")
	//Flag after which you have to specify the location of your private key key.
	credUpdateCmd.Flags().StringVar(&privateCert, "private", "", "Location to new private key.")
	//Flag after which you have to specify the user name.
	credUpdateCmd.Flags().StringVar(&userName, "username", "", "New username.")
	//Flag after which you have to specify password.
	credUpdateCmd.Flags().StringVar(&passWord, "password", "", "New password.")
	CredentialsRootCmd.AddCommand(credUpdateCmd)
}

var credUpdateCmd = &cobra.Command{
	Use:   "update [CREDENTIALS-ID]",
	Short: "Update credentials.",
	Long:  "Update credentials.",

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
		id, err = credentials.EditCredetialsID(id, publicCert, privateCert, userName, passWord)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Credentials updated: " + newID)
		}
	},
}
