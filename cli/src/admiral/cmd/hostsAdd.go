package cmd

import (
	"fmt"

	"admiral/hosts"

	"github.com/spf13/cobra"
)

var (
	ipF         string
	resPoolF    string
	deplPolicyF string
	autoAccept  bool
)

func init() {
	//Flag after which you have to specify the location of your public key.
	hostAddCmd.Flags().StringVar(&publicCert, "public", "", "(Required if adding new credentials)"+publicCertDesc)
	//Flag after which you have to specify the location of your private key key.
	hostAddCmd.Flags().StringVar(&privateCert, "private", "", "(Required if adding new credentials)"+privateCertDesc)
	//hostAddCmd after which you have to specify the user name.
	hostAddCmd.Flags().StringVar(&userName, "username", "", "(Required if adding new credentials)"+"Username.")
	//Flag after which you have to specify password.
	hostAddCmd.Flags().StringVar(&passWord, "password", "", "(Required if adding new credentials)"+"Password.")

	hostAddCmd.Flags().StringVar(&ipF, "ip", "", "(Required) Address of host.")
	hostAddCmd.Flags().StringVar(&resPoolF, "resource-pool", "", "(Required) Resource pool ID.")
	hostAddCmd.Flags().StringVar(&credName, "credentials", "", "(Required if using existing one.) Credentials ID.")
	hostAddCmd.Flags().StringVar(&deplPolicyF, "deployment-policy", "", "Deployment policy ID.")
	hostAddCmd.Flags().BoolVar(&autoAccept, "accept", false, "Auto accept if certificate is not trusted.")
	//Flag for custom properties.
	hostAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	HostsRootCmd.AddCommand(hostAddCmd)
}

var hostAddCmd = &cobra.Command{
	Use:   "add",
	Short: "Add host",
	Long:  "Add host",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
		)
		newID, err = hosts.AddHost(ipF, resPoolF, deplPolicyF, credName, publicCert, privateCert, userName, passWord,
			autoAccept,
			custProps)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Host added: " + newID)
		}
	},
}
