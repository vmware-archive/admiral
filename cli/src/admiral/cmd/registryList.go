package cmd

import (
	"admiral/help"
	"admiral/registries"

	"github.com/spf13/cobra"
)

func init() {
	regListCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	RegistriesRootCmd.AddCommand(regListCmd)
}

var regListCmd = &cobra.Command{
	Use:   "ls",
	Short: "Lists existing registries.",
	Long:  "Lists existing registries.",

	Run: func(cmd *cobra.Command, args []string) {
		rl := &registries.RegistryList{}
		rl.FetchRegistries()
		rl.Print()
	},
}
