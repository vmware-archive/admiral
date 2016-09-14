package cmd

import (
	"fmt"

	"admiral/help"
	"admiral/policies"

	"github.com/spf13/cobra"
)

func init() {
	policyListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	PoliciesRootCmd.AddCommand(policyListCmd)
}

var policyListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing policies.",
	Long:  "Lists existing policies.",

	Run: func(cmd *cobra.Command, args []string) {
		pl := &policies.PolicyList{}
		count := pl.FetchPolices()
		if count < 0 {
			fmt.Println("n/a")
		}
		pl.Print()
	},
}
