package cmd

import (
	"fmt"

	"github.com/spf13/cobra"

	"admiral/track"
)

func init() {
	RootCmd.AddCommand(waitCmd)
}

var waitCmd = &cobra.Command{
	Use:   "wait [TASK-ID]",
	Short: "Wait until task has finished.",
	Long:  "Wait until task has finished.",

	//Main function for the "wait" command.
	//This command is used to wait for specific task to be finished.
	//It takes the track ID as parameter where specific commands provide it after they are called.
	//Every 3 seconds it will check for the status of the given task.
	//When it's either FAILED or FINISHED or CANELLED it will end with printing the status of the task.
	Run: func(cmd *cobra.Command, args []string) {
		var (
			taskId string
			ok     bool
		)
		if taskId, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter task id.")
			return
		}
		track.Wait(taskId)
	},
}
