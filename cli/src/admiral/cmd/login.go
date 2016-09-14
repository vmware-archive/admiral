package cmd

import (
	"bufio"
	"fmt"
	"os"
	"strings"
	"syscall"

	"admiral/config"
	"admiral/loginout"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh/terminal"
)

var (
	username  string
	password  string
	showToken bool
)

func init() {
	loginCmd.Flags().StringVarP(&username, "user", "u", "", "Username")
	loginCmd.Flags().StringVarP(&password, "pass", "p", "", "Password")
	loginCmd.Flags().StringVar(&urlF, "url", "", "Set URL config property.")
	loginCmd.Flags().BoolVar(&showToken, "status", false, "Print information about current user.")
	RootCmd.AddCommand(loginCmd)
}

//Function that prompt the user to enter his username.
func prompUsername() string {
	var username string
	reader := bufio.NewReader(os.Stdin)
	fmt.Println("Enter username:")
	username, _ = reader.ReadString('\n')
	return username
}

//Function that prompt the user to enter his password.
func promptPassword() string {
	var password string
	fmt.Println("Enter password:")
	bytePassword, _ := terminal.ReadPassword(int(syscall.Stdin))
	password = string(bytePassword)
	return password
}

var loginCmd = &cobra.Command{
	Use:   "login",
	Short: "Login with username and pass",
	Long:  "Login with username and pass",

	//Main function for the "login" command.
	//You can either be prompted to enter your username and password or to enter them as arguments to -u or --user and -p or --pass flags.
	//If username and password are correct a temp file will be created in the OS temp directory where the auth token will be held.
	//If the username and/or password are incorrect again temp file will be created but it will be empty and user won't be authorized with missing token.
	Run: func(cmd *cobra.Command, args []string) {
		if showToken {
			fmt.Println(loginout.GetInfo())
			return
		}

		if config.USER != "" {
			fmt.Println(config.USER)
		}
		if strings.TrimSpace(username) == "" {
			if config.USER == "" {
				username = prompUsername()
			} else {
				username = config.USER
			}
		}
		if strings.TrimSpace(password) == "" {
			password = promptPassword()
		}
		loginout.Login(username, password, urlF)

	},
}
