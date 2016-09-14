package cmd

import (
	"fmt"

	"admiral/policies"

	"github.com/spf13/cobra"
)

func init() {
	PoliciesRootCmd.AddCommand(polRemoveCmd)
}

var polRemoveCmd = &cobra.Command{
	Use:   "rm [POLICY-ID]",
	Short: "Remove existing pool",
	Long:  "Remove existing pool",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter policy ID.")
			return
		}
		newID, err = policies.RemovePolicyID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Policy removed: " + newID)
		}
	},
}
