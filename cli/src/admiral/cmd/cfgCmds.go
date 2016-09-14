package cmd

import (
	"fmt"
	"strings"

	"admiral/config"

	"github.com/spf13/cobra"
)

var (
	keyProp string
	valProp string
)

func init() {
	cfgGetCmd.Flags().StringVarP(&keyProp, "key", "k", "", "(Required) Key")

	cfgSetCmd.Flags().StringVarP(&keyProp, "key", "k", "", "(Required) Key")
	cfgSetCmd.Flags().StringVarP(&valProp, "value", "v", "", "(Required) Value")

	ConfigRootCmd.AddCommand(cfgGetCmd)
	ConfigRootCmd.AddCommand(cfgSetCmd)
	ConfigRootCmd.AddCommand(cfgInspectCmd)
}

var cfgGetCmd = &cobra.Command{
	Use:   "get",
	Short: "Set value from provided key key.",
	Long:  "Set value from provided key key.",
	Run: func(cmd *cobra.Command, args []string) {
		if keyProp == "" {
			fmt.Println("Please enter any key.")
			return
		}

		v := config.GetProperty(strings.Title(keyProp))

		if v.IsValid() {
			fmt.Println(v.String())
		} else {
			fmt.Println("Invalid key.")
		}

	},
}

var cfgSetCmd = &cobra.Command{
	Use:   "set",
	Short: "Set value to provided key.",
	Long:  "Set value to provided key.",
	Run: func(cmd *cobra.Command, args []string) {
		if keyProp == "" {
			fmt.Println("Please enter any key.")
			return
		}
		if valProp == "" {
			fmt.Println("Please enter any value.")
			return
		}

		isSet := config.SetProperty(strings.Title(keyProp), valProp)
		if isSet {
			fmt.Println("New property set successfully.")
		}
	},
}

var cfgInspectCmd = &cobra.Command{
	Use:   "inspect",
	Short: "Shows current config properties",
	Long:  "Shows current config properties",
	Run: func(cmd *cobra.Command, args []string) {
		jsonBody := config.Inspect()
		fmt.Println(string(jsonBody))
	},
}
