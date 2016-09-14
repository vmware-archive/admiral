package cmd

import (
	"strings"

	"admiral/logs"

	"github.com/spf13/cobra"
)

var since int32

func init() {
	logsCmd.Flags().Int32VarP(&since, "since", "s", 15, "Since when to show logs.")
	RootCmd.AddCommand(logsCmd)
}

var logsCmd = &cobra.Command{
	Use:   "logs [CONTAINER]",
	Short: "Fetch the logs of a container",
	Long:  "Fetch the logs of a container",

	Run: func(cmd *cobra.Command, args []string) {
		contName := strings.Join(args, " ")
		sinceSecs := since * 60
		logs.GetLog(contName, string(sinceSecs))
	},
}
