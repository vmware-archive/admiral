package cmd

import (
	"fmt"
	"strings"

	"admiral/images"

	"github.com/spf13/cobra"
)

func init() {
	RootCmd.AddCommand(searchCmd)
}

var searchCmd = &cobra.Command{
	Use:   "search [IMAGE-NAME]",
	Short: "Search for image.",
	Long:  "Search for image.",

	Run: func(cmd *cobra.Command, args []string) {
		if len(args) < 1 {
			images.PrintPopular()
			return
		}
		query := strings.Join(args, " ")
		il := &images.ImagesList{}
		count := il.QueryImages(query)
		if count < 1 {
			fmt.Println("n/a")
			return
		}
		il.Print()
	},
}
