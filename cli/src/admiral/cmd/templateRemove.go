package cmd

import (
	"fmt"

	"admiral/templates"

	"github.com/spf13/cobra"
)

func init() {
	TemplatesRootCmd.AddCommand(tmplRmCmd)
}

var tmplRmCmd = &cobra.Command{
	Use:   "rm [TEMPLATE-ID]",
	Short: "Remove template.",
	Long:  "Remove template.",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			newID string
			err   error
			id    string
			ok    bool
		)

		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter template ID.")
			return
		}
		newID, err = templates.RemoveTemplateID(id)

		if err != nil {
			fmt.Println(err)
		} else {
			fmt.Println("Template removed: " + newID)
		}
	},
}
