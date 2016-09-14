package cmd

import (
	"fmt"

	"admiral/containers"

	"bufio"
	"os"
	"strings"

	"github.com/spf13/cobra"
)

var execF string
var interact bool

func init() {
	contExecCmd.Flags().StringVar(&execF, "cmd", "", "Command to execute.")
	contExecCmd.Flags().BoolVarP(&interact, "interactive", "i", false, "Interactive mode.")
	RootCmd.AddCommand(contExecCmd)
}

var contExecCmd = &cobra.Command{
	Use:   "exec [CONTAINER-ID]",
	Short: "Run a command in a running container.",
	Long:  "Run a command in a running container.",

	Run: func(cmd *cobra.Command, args []string) {
		if len(args) < 1 {
			fmt.Println("Enter container ID.")
			return
		}

		var (
			ok bool
			id string
		)
		if id, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter container.")
			return
		}

		if interact {
			interactive(id)
			return
		}
		containers.ExecuteCmd(id, execF)
	},
}

func interactive(id string) {
	reader := bufio.NewReader(os.Stdin)
	var input string
	fmt.Print(">")
	input, _ = reader.ReadString('\n')
	for {
		if strings.TrimSpace(input) == "exit" {
			break
		}
		containers.ExecuteCmd(id, strings.TrimSpace(input))
		fmt.Print(">")
		input, _ = reader.ReadString('\n')
	}
}
