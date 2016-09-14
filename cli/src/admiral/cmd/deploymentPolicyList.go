package cmd

import (
	"fmt"

	"admiral/deplPolicy"
	"admiral/help"

	"github.com/spf13/cobra"
)

func init() {
	dpListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	DeploymentPoliciesRootCmd.AddCommand(dpListCmd)
}

var dpListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing deployment policies.",
	Long:  "Lists existing deployment policies.",

	Run: func(cmd *cobra.Command, args []string) {
		dpl := &deplPolicy.DeploymentPolicyList{}
		count := dpl.FetchDP()
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		dpl.Print()
	},
}
