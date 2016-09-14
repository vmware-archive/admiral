package cmd

import (
	"fmt"

	"admiral/certificates"
	"admiral/help"

	"github.com/spf13/cobra"
)

func init() {
	certListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	CertsRootCmd.AddCommand(certListCmd)
}

var certListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing certificates",
	Long:  "Lists existing certificates",

	Run: func(cmd *cobra.Command, args []string) {
		cl := certificates.CertificateList{}
		count := cl.FetchCertificates()
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		cl.Print()
	},
}
