package functions

import (
	"fmt"
	"os"
)

//Check for error raised by response.
//Print message.
//Currently panic too used for debugging.
func CheckResponse(err error) {
	if err != nil {
		fmt.Println("Response error occured.")
		fmt.Println(err.Error())
		panic(err.Error())
		os.Exit(-2)
	}
}

//Check for error raised by reading/writing json.
//Print message.
//Currently panic too used for debugging.
func CheckJson(err error) {
	if err != nil {
		fmt.Println("Json error when reading and/or writing.")
		fmt.Println(err.Error())
		panic(err.Error())
		os.Exit(-2)
	}
}

//Check for error raised by operations with files.
//Print message.
//Currently panic too used for debugging.
func CheckFile(err error) {
	if err != nil {
		fmt.Println("Error on read/write file.")
		fmt.Println(err.Error())
		os.Exit(-2)
	}
}

func CheckParse(err error) {
	if err != nil {
		fmt.Println(err.Error())
		os.Exit(-2)
	}
}
