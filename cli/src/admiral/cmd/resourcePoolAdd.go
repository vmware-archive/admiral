package cmd

import (
	"admiral/resourcePools"

	"fmt"

	"github.com/spf13/cobra"
)

func init() {
	//Flag for custom properties.
	rpAddCmd.Flags().StringSliceVar(&custProps, "cp", []string{}, custPropsDesc)
	ResourcePoolsRootCmd.AddCommand(rpAddCmd)
}

var rpAddCmd = &cobra.Command{
	Use: "add [NAME]",

	Short: "Add resource pool by given name.",

	Long: "Add resource pool by given name.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			rpName string
			ok     bool
		)
		if rpName, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter resource pool name.")
			return
		}
		id, err := resourcePools.AddRP(rpName, custProps)
		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Resource pool added: " + id)
		}
	},
}
