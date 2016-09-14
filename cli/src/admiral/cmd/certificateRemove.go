package cmd

import (
	"fmt"

	"admiral/certificates"

	"github.com/spf13/cobra"
)

func init() {
	CertsRootCmd.AddCommand(certRemoveCmd)
}

var certRemoveCmd = &cobra.Command{
	Use:   "rm [CERTIFICATE-ID]",
	Short: "Remove certificate.",
	Long:  "Remove certificate.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			ok    bool
			id    string
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter certificate ID.")
			return
		}
		newID, err = certificates.RemoveCertificateID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Certificate removed: " + newID)
		}
	},
}
