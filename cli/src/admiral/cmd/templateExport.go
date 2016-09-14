package cmd

import (
	"fmt"

	"admiral/apps"

	"github.com/spf13/cobra"
)

func init() {
	exportAppCmd.Flags().StringVar(&formatTemplate, "format", "yaml", "(Required) File format - yaml/docker")
	exportAppCmd.Flags().StringVar(&dirF, "file", "", "(Required) path/to/file")
	TemplatesRootCmd.AddCommand(exportAppCmd)
}

//Function to verify the given template in the flag.
//Returns true if format is valid, false if invalid.
func verifyFormat() bool {
	if formatTemplate != "yaml" && formatTemplate != "docker" {
		fmt.Println("Choose either yaml or docker file format.")
		return false
	}
	return true
}

var exportAppCmd = &cobra.Command{
	Use:   "export [TEMPLATE-ID]",
	Short: "Download exported application.",
	Long:  "Download exported application.",

	//Main function for the command "export". Args are joined with space and this is the application name.
	//For now exports the the first with that name.
	Run: func(cmd *cobra.Command, args []string) {
		if !verifyFormat() {
			return
		}
		var (
			id string
			ok bool
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter template ID.")
			return
		}
		newID, err := apps.Export(id, dirF, formatTemplate)
		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Template exported: " + newID)
		}
	},
}
