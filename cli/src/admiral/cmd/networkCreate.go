package cmd

import (
	"fmt"

	"admiral/help"
	"admiral/network"

	"github.com/spf13/cobra"
)

var (
	gateways []string
	subnets  []string
	options  []string
)

func init() {
	networkCreateCmd.Flags().StringSliceVar(&gateways, "gateway", []string{}, "Gateway for the master subnet.")
	networkCreateCmd.Flags().StringSliceVar(&subnets, "subnet", []string{}, "Subnet in CIDR format that represents a network segment.")
	networkCreateCmd.Flags().StringSliceVarP(&options, "opt", "o", []string{}, "Set driver options. Format: \"key:value\"")
	networkCreateCmd.SetHelpTemplate(help.NetworkUsageTmpl)
	NetworksRootCmd.AddCommand(networkCreateCmd)
}

var networkCreateCmd = &cobra.Command{
	Use:   "create [NAME]",
	Short: "Create a network",
	Long:  "Create a network",

	Run: func(cmd *cobra.Command, args []string) {
		var (
			name string
			ok   bool
		)
		if name, ok = ValidateArgsCount(args); !ok {
			fmt.Println("Enter network.")
			return
		}
		n := &network.Network{}
		n.SetName(name)
		n.SetOptions(options)
		n.SetIPAMConfig(subnets, gateways)
		isCreated, msg := n.Create()
		if isCreated {
			fmt.Println("Network created successfully.")
		} else {
			fmt.Println("Error when creating network.")
			if msg != "" {
				fmt.Println(msg)
			}
		}
	},
}
