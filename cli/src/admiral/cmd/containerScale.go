package cmd

import (
	"fmt"

	"admiral/containers"

	"github.com/spf13/cobra"
)

var scaleCount int32

func init() {
	scaleCmd.Flags().Int32VarP(&scaleCount, "count", "c", 0, "(Required) Resource count.")
	scaleCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(scaleCmd)
}

var scaleCmd = &cobra.Command{
	Use:   "scale [CONTAINER-ID]",
	Short: "Scale existing container",
	Long:  "Scale existing container",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			id    string
			ok    bool
			newID string
			err   error
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter container.")
			return
		}

		if scaleCount < 1 {
			fmt.Println("Please provide scale count > 0")
			return
		}

		newID, err = containers.ScaleContainer(id, scaleCount, asyncTask)

		if err != nil {
			fmt.Println(err)
		} else {
			if asyncTask {
				fmt.Println("Container is being scaled: " + newID)
			} else {
				fmt.Println("Container scaled: " + newID)
			}
		}

	},
}
