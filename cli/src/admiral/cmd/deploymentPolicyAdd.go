package cmd

import (
	"fmt"

	"admiral/deplPolicy"

	"github.com/spf13/cobra"
)

var (
	dpName        string
	dpDescription string
)

func init() {
	dpAddCmd.Flags().StringVar(&dpDescription, "description", "", "(Required) Deployment policy description.")
	DeploymentPoliciesRootCmd.AddCommand(dpAddCmd)
}

var dpAddCmd = &cobra.Command{
	Use:   "add [NAME]",
	Short: "Adds deployment policy.",
	Long:  "Adds deployment policy.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			id     string
			err    error
			dpName string
			ok     bool
		)
		if dpName, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter deployment policy name.")
			return
		}
		id, err = deplPolicy.AddDP(dpName, dpDescription)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Deployment policy added: " + id)
		}
	},
}
