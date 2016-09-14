package cmd

import (
	"fmt"

	"admiral/containers"

	"admiral/functions"

	"strings"

	"github.com/spf13/cobra"
)

func init() {
	removeContainerCmd.Flags().StringVarP(&queryF, "query", "q", "", "Every container that match the query will be removed.")
	removeContainerCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	RootCmd.AddCommand(removeContainerCmd)
}

var removeContainerCmd = &cobra.Command{
	Use: "rm [CONTAINER-ID]...",

	Short: "Remove existing container(s).",

	Long: "Remove existing container(s).",

	//Main function for "rm" command.
	//Args are the names of containers.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			resIDs []string
			err    error
		)
		if queryF != "" {
			resIDs, err = containers.RemoveMany(queryF, asyncTask)
		} else {
			if len(args) > 0 {
				fmt.Printf("Are you sure you want to remove %s? (y/n)\n", strings.Join(args, " "))
				answer := functions.PromptAgreement()
				if answer == "n" || answer == "no" {
					fmt.Println("Remove command aborted!")
					return
				}
				resIDs, err = containers.RemoveContainer(args, asyncTask)
			} else {
				fmt.Println("Enter container(s) ID.")
				return
			}
		}

		if err != nil {
			fmt.Println(err)
		} else {
			if asyncTask {
				fmt.Println("Container(s) are being removed: " + strings.Join(resIDs, " "))
			} else {
				fmt.Println("Container(s) removed: " + strings.Join(resIDs, " "))
			}
		}
	},
}
