package cmd

import (
	"bytes"
	"encoding/json"
	"fmt"

	"admiral/credentials"
	"admiral/functions"
	"admiral/hosts"

	"admiral/resourcePools"

	"github.com/spf13/cobra"
)

var (
	cpHostIP    string
	cpCredID    string
	cpResPoolID string
)

func init() {
	custmPropList.Flags().StringVar(&cpHostIP, "host", "", "IP of the host that you want to manage custom properties.")
	custmPropList.Flags().StringVar(&cpCredID, "credentials", "", "ID of the credentials that you want to manage custom properties.")
	custmPropList.Flags().StringVar(&cpResPoolID, "resource-pool", "", "ID of the resource pool that you want to manage custom properties.")
	CustomPropertiesRootCmd.AddCommand(custmPropList)
}

var custmPropList = &cobra.Command{
	Use:   "ls",
	Short: "Lists current properties of given entity.",
	Long:  "Lists current properties of given entity.",

	Run: func(cmd *cobra.Command, args []string) {
		if cpHostIP != "" {
			fmt.Println(hostCpString())
		}

		if cpCredID != "" {
			fmt.Println(credCpString())
		}

		if cpResPoolID != "" {
			fmt.Println(rpCpString())
		}
	},
}

func hostCpString() string {
	cpHost := hosts.GetPublicCustomProperties(cpHostIP)
	if cpHost == nil {
		return "Host with this IP not found."
	}
	cpJson, err := json.MarshalIndent(cpHost, "", "    ")
	functions.CheckJson(err)
	buffer := bytes.Buffer{}
	buffer.WriteString(fmt.Sprintf("Custom Properties of Host: %s\n", cpHostIP))
	buffer.WriteString(fmt.Sprint(string(cpJson)))
	return buffer.String()
}

func credCpString() string {
	cpCred := credentials.GetPublicCustomProperties(cpCredID)
	if cpCred == nil {
		return "Credentials with this ID not found."
	}
	cpJson, err := json.MarshalIndent(cpCred, "", "    ")
	functions.CheckJson(err)
	buffer := bytes.Buffer{}
	buffer.WriteString(fmt.Sprintf("Custom Properties of Credentials: %s\n", cpCredID))
	buffer.WriteString(fmt.Sprint(string(cpJson)))
	return buffer.String()
}

func rpCpString() string {
	cpRp := resourcePools.GetPublicCustomProperties(cpResPoolID)
	if cpRp == nil {
		return "Resource pool with this ID not found."
	}
	cpJson, err := json.MarshalIndent(cpRp, "", "    ")
	functions.CheckJson(err)
	buffer := bytes.Buffer{}
	buffer.WriteString(fmt.Sprintf("Custom Properties of Resource pool: %s\n", cpCredID))
	buffer.WriteString(fmt.Sprint(string(cpJson)))
	return buffer.String()
}
