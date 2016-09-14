package cmd

import (
	"fmt"

	"admiral/events"

	"github.com/spf13/cobra"
)

func init() {
	eventCmd.Flags().BoolVar(&clearAll, "clear", false, "Clear all logged requests.")
	RootCmd.AddCommand(eventCmd)
}

var eventCmd = &cobra.Command{
	Use:   "events",
	Short: "Prints events log.",
	Long:  "Prints events log.",

	Run: func(cmd *cobra.Command, args []string) {
		el := events.EventList{}
		count := el.FetchEvents()
		if clearAll {
			el.ClearAllEvent()
			return
		}
		if count < 1 {
			fmt.Println("n/a")
		}
		el.Print()
	},
}
