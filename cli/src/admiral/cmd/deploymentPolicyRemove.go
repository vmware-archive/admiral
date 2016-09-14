package cmd

import (
	"fmt"

	"admiral/deplPolicy"

	"github.com/spf13/cobra"
)

func init() {
	DeploymentPoliciesRootCmd.AddCommand(dpRemoveCmd)
}

var dpRemoveCmd = &cobra.Command{
	Use:   "rm [DEPLOYMENT-POLICY-ID]",
	Short: "Removes existing depoyment policy.",
	Long:  "Removes existing depoyment policy.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter deployment policy ID.")
			return
		}
		newID, err = deplPolicy.RemoveDPID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Deployment policy removed: " + newID)
		}
	},
}
