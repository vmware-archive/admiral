package cmd

import (
	"fmt"

	"admiral/certificates"

	"github.com/spf13/cobra"
)

func init() {
	certAddCmd.Flags().StringVarP(&urlF, "url", "u", "", "Url to import certificate.")
	certAddCmd.Flags().StringVarP(&dirF, "file", "f", "", "File to import certificate.")
	CertsRootCmd.AddCommand(certAddCmd)
}

var certAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Add certificate",
	Long:  "Add certificate",

	Run: func(cmd *cobra.Command, args []string) {
		if urlF != "" && dirF != "" {
			fmt.Println("--file and --url flags are exclusive, provide only one of them.")
			return
		}

		var (
			id  string
			err error
		)
		if dirF != "" {
			id, err = certificates.AddFromFile(dirF)
		} else if urlF != "" {
			id, err = certificates.AddFromUrl(urlF)
		} else {
			fmt.Println("Provide url or file to add certificate.")
		}

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Certificate added: " + id)
		}
	},
}
