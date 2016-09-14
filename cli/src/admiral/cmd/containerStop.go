package cmd

import (
	"fmt"
	"admiral/containers"
	"strings"

	"github.com/spf13/cobra"
)

func init() {
	stopContainerCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(stopContainerCmd)
}

var stopContainerCmd = &cobra.Command{
	Use:   "stop [CONTAINER-ID]",
	Short: "Stops existing container",
	Long:  "Stops existing container",
	//Main function to stop existing container by provided name.
	//At this state it will try to stop all of the given containers, without check their current state or if non-unique names are given.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			resIDs []string
			err    error
		)

		if len(args) > 0 {
			resIDs, err = containers.StopContainer(args, asyncTask)
		} else {
			fmt.Println("Enter container(s) ID.")
			return
		}

		if err != nil {
			fmt.Println(err)
		} else {
			if asyncTask {
				fmt.Println("Container(s) are being stopped: " + strings.Join(resIDs, " "))
			} else {
				fmt.Println("Container(s) stopped: " + strings.Join(resIDs, " "))
			}
		}

	},
}
