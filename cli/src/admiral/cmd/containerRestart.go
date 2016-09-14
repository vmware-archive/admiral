package cmd

import (
	"admiral/containers"

	"fmt"
	"strings"

	"github.com/spf13/cobra"
)

func init() {
	resContCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(resContCmd)
}

var resContCmd = &cobra.Command{
	Use:   "restart [CONTAINER-ID]",
	Short: "Restart container.",
	Long:  "Restart container.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			resIDs []string
			err    error
		)
		if len(args) > 0 {
			resIDs, err = containers.StopContainer(args, asyncTask)
			resIDs, err = containers.StartContainer(args, asyncTask)
		} else {
			fmt.Println("Enter container(s) ID.")
			return
		}

		if err != nil {
			fmt.Println(err)
		} else {
			if asyncTask {
				fmt.Println("Container(s) are being started: " + strings.Join(resIDs, " "))
			} else {
				fmt.Println("Container(s) started: " + strings.Join(resIDs, " "))
			}
		}

	},
}
