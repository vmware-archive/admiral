package cmd

import (
	"fmt"

	"admiral/containers"

	"github.com/spf13/cobra"
)

func init() {
	RootCmd.AddCommand(inspcContCmd)
}

var inspcContCmd = &cobra.Command{
	Use:   "inspect [CONTAINER-ID]",
	Short: "Return low-level information on a container.",
	Long:  "Return low-level information on a container.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			ok bool
			id string
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter container.")
			return
		}
		output, err := containers.InspectContainer(id)
		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println(output)
		}
	},
}
