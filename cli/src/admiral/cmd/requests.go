package cmd

import (
	"fmt"

	"admiral/requests"

	"github.com/spf13/cobra"
)

var (
	startedOnly  bool
	finishedOnly bool
	failedOnly   bool
	clearAll     bool
)

func init() {
	reqCmd.Flags().BoolVar(&clearAll, "clear", false, "Clear all logged requests.")
	reqCmd.Flags().BoolVar(&startedOnly, "started", false, "Show started only requests.")
	reqCmd.Flags().BoolVar(&finishedOnly, "finished", false, "Show finished only requests.")
	reqCmd.Flags().BoolVar(&failedOnly, "failed", false, "Show failed only requests.")
	RootCmd.AddCommand(reqCmd)
}

func allFalse() bool {
	if !startedOnly && !finishedOnly && !failedOnly {
		return true
	}
	return false
}

var reqCmd = &cobra.Command{
	Use:   "requests",
	Short: "Prints request log.",
	Long:  "Prints request log.",

	Run: func(cmd *cobra.Command, args []string) {
		rl := &requests.RequestsList{}
		count := rl.FetchRequests()
		if count < 1 {
			fmt.Println("n/a")
			return
		}

		if clearAll {
			rl.ClearAllRequests()
			return
		}

		if allFalse() {
			rl.PrintAll()
		} else {
			if startedOnly {
				rl.PrintStartedOnly()
			}
			if failedOnly {
				rl.PrintFailedOnly()
			}
			if finishedOnly {
				rl.PrintFinishedOnly()
			}
		}
	},
}
