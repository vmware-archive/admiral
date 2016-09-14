package cmd

import (
	"fmt"

	"admiral/apps"

	"github.com/spf13/cobra"
)

var keepTemplate bool

func init() {
	appRunCmd.Flags().BoolVar(&asyncTask, "async", false, asyncDesc)
	appRunCmd.Flags().StringVar(&dirF, "file", "", "Provision template from file.")
	appRunCmd.Flags().BoolVar(&keepTemplate, "keep", false, "Do not remove template after provisioning.")
	appRunCmd.Flags().StringVar(&groupID, "group", "", "(Required) "+groupIDDesc)
	AppsRootCmd.AddCommand(appRunCmd)
}

var appRunCmd = &cobra.Command{
	Use:   "run [TEMPLATE-ID]",
	Short: "Provision application from template.",
	Long:  "Provision application from template.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			IDs []string
			err error
			ok  bool
			id  string
		)

		if dirF != "" {
			IDs, err = apps.RunAppFile(dirF, keepTemplate, asyncTask)
		} else {
			if id, ok = ValidateArgsCount(args); !ok {
				fmt.Println("Enter template ID.")
				return
			}
			IDs, err = apps.RunAppID(id, asyncTask)
		}

		if err != nil {
			fmt.Println(err)
		} else if len(IDs) > 0 {
			if asyncTask {
				fmt.Println("Application is provisioning: " + IDs[0])
			} else {
				fmt.Println("Application provisioned: " + IDs[0])
			}
		}
	},
}
