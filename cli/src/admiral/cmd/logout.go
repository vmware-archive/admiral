package cmd

import (
	"admiral/loginout"

	"github.com/spf13/cobra"
)

func init() {
	RootCmd.AddCommand(logoutCmd)
}

var logoutCmd = &cobra.Command{
	Use:   "logout",
	Short: "Logout user",
	Long:  "Logout user",

	//Main function of the "logout" command.
	//It simply delete the file where auth token is held.
	Run: func(cmd *cobra.Command, args []string) {
		loginout.Logout()
	},
}
