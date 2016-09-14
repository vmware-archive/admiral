package cmd

import (
	"fmt"

	"admiral/certificates"

	"github.com/spf13/cobra"
)

func init() {
	certUpdateCmd.Flags().StringVarP(&urlF, "url", "u", "", "Url to import certificate. (NOT IMPLEMENTED YET).")
	certUpdateCmd.Flags().StringVarP(&dirF, "file", "f", "", "File to import certificate.")
	CertsRootCmd.AddCommand(certUpdateCmd)
}

var certUpdateCmd = &cobra.Command{
	Use:   "update [CERTIFICATE-ID]",
	Short: "Update certificate.",
	Long:  "Update certificate.",

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
		newID, err = certificates.EditCertificateID(id, dirF, urlF)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Certificate updated: " + newID)
		}
	},
}
