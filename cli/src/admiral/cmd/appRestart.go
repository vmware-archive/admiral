package cmd

import (
	"fmt"

	"admiral/apps"

	"github.com/spf13/cobra"
)

func init() {
	resAppCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	AppsRootCmd.AddCommand(resAppCmd)
}

var resAppCmd = &cobra.Command{
	Use:   "restart [APPLICATION-ID]",
	Short: "Restarts application.",
	Long:  "Restarts application.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			IDs []string
			err error
			ok  bool
			id  string
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter application ID.")
			return
		}
		IDs, err = apps.StopAppID(id, asyncTask)
		IDs, err = apps.StartAppID(id, asyncTask)

		if err != nil {
			fmt.Println(err)
		} else if len(IDs) > 0 {
			if asyncTask {
				fmt.Println("Application is being restarted: " + IDs[0])
			} else {
				fmt.Println("Application restarted: " + IDs[0])
			}
		}
	},
}
