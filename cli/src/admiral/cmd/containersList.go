package cmd

import (
	"fmt"

	"admiral/containers"
	"admiral/help"

	"github.com/spf13/cobra"
)

var allContainers bool

func init() {
	listContainerCmd.Flags().BoolVarP(&allContainers, "all", "a", false, "Show all containers.")
	listContainerCmd.Flags().StringVarP(&queryF, "query", "q", "", "Add query.")
	listContainerCmd.SetUsageTemplate(help.DefaultUsageListTemplate)
	RootCmd.AddCommand(listContainerCmd)
}

var listContainerCmd = &cobra.Command{
	Use:   "ps",
	Short: "Lists existing containers.",
	Long:  "Lists existing containers.",

	//Main function for "ls" command. It doesn't require any arguments.
	Run: func(cmd *cobra.Command, args []string) {
		lc := &containers.ListContainers{}
		count := lc.FetchContainers(queryF)
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		lc.Print(allContainers)
	},
}
