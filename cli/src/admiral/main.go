package main

import (
	"fmt"

	"admiral/cmd"
	"admiral/config"
)

func main() {
	config.GetCfg()
	err := cmd.RootCmd.Execute()
	if err != nil {
		fmt.Println(err.Error())
	}
}
