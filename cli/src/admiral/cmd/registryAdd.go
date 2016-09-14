package cmd

import (
	"fmt"

	"admiral/registries"

	"github.com/spf13/cobra"
)

var (
	addressF string
)

func init() {
	//Flag after which you have to specify the location of your public key.
	regAddCmd.Flags().StringVar(&publicCert, "public", "", "(Required if adding new credentials)"+publicCertDesc)
	//Flag after which you have to specify the location of your private key key.
	regAddCmd.Flags().StringVar(&privateCert, "private", "", "(Required if adding new credentials)"+privateCertDesc)
	//hostAddCmd after which you have to specify the user name.
	regAddCmd.Flags().StringVar(&userName, "username", "", "(Required if adding new credentials)"+"Username.")
	//Flag after which you have to specify password.
	regAddCmd.Flags().StringVar(&passWord, "password", "", "(Required if adding new credentials)"+"Password.")

	regAddCmd.Flags().StringVar(&addressF, "address", "", "(Required) Address of registry.")
	regAddCmd.Flags().StringVar(&credName, "credentials", "", "(Required if using existing one.) Credentials ID.")
	regAddCmd.Flags().BoolVar(&autoAccept, "accept", false, "Auto accept if certificate is not trusted.")
	//Flag for custom properties.
	regAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	RegistriesRootCmd.AddCommand(regAddCmd)
}

var regAddCmd = &cobra.Command{
	Use:   "add [NAME]",
	Short: "Add registry",
	Long:  "Add registry",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			name string
			ok   bool
		)
		if name, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter policy name.")
			return
		}
		newID, err := registries.AddRegistry(name, addressF, credName, publicCert, privateCert, userName, passWord, autoAccept)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Registry added: " + newID)
		}
	},
}
