package functions

import (
	"fmt"
	"strings"
)

//PrintID prints the provided ID as parameter in the format
// "New entity ID: %s\n".
func PrintID(id string) {
	fmt.Printf("New entity ID: %s\n", id)
}

//PromptAgreement is asking the user to enter either "y"/"yes" or "n"/"no".
//Returns the user's answer.
func PromptAgreement() string {
	var answer string
	for {
		fmt.Scanf("%s", &answer)
		answer = strings.ToLower(answer)
		if answer == "yes" || answer == "y" || answer == "no" || answer == "n" {
			break
		}
	}
	return answer
}

//ValidateArgsCount takes array of arguments and return the first element and true
//if their count is more than 0 or empty string and false if the count is less than 0.
func ValidateArgsCount(args []string) (string, bool) {
	if len(args) > 0 {
		return args[0], true
	}
	return "", false
}
