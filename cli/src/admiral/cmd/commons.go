package cmd

var (
	//Flag to wait for task
	asyncTask bool
	asyncDesc string = "Wait until the task is finished."

	//Used to specify format of exported app.
	formatTemplate string

	//Used to specify file.
	dirF string

	//Flag to decide to list included containers
	inclCont bool

	//Flag for query.
	queryF string

	//Flag for url
	urlF string

	//Flag for verbose option.
	verbose bool

	//Flag to execute commands by enitity's self link, in order to avoid duplicates.
	//selfID     string
	//selfIDDesc string = "Executing command by ID will avoid duplicate names conflict."

	//Flag to store custom properties.
	custProps     []string
	custPropsDesc string = "Add some custom properties"

	//Flag used to update name.
	newName string

	//Flag for group.
	groupID     string
	groupIDDesc string = "Group ID."
)

var admiralLogo = `




       *****
     ***###***           @@      @@@@    @      @  @  @@@@       @@     @
   ******#******         @@      @   @   @@    @@  @  @   @      @@     @
   ****#*#*#****        @  @     @    @  @ @  @ @  @  @    @    @  @    @
   *****###*****        @  @     @    @  @ @  @ @  @  @   @     @  @    @
    ***********         @  @     @    @  @  @@  @  @  @@@@@     @  @    @
    *         *        @@@@@@    @    @  @      @  @  @  @     @@@@@@   @
    ***********       @      @   @   @   @      @  @  @   @    @    @   @
    ***********      @        @  @@@@    @      @  @  @    @  @      @  @@@@@
      *******

                             Github: https://github.com/vmware/admiral
                             Wiki: https://github.com/vmware/admiral/wiki

`

func ValidateArgsCount(args []string) (string, bool) {
	if len(args) > 0 {
		return args[0], true
	}
	return "", false
}
