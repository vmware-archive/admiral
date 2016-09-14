# Overview

The Admiral CLI is tool used to manage your Admiral from the console or to create scripts which will automate some processes. This guide describes what prerequisites you need in order to build it, how to build it and how to use it.

# Prerequisites

The Admiral CLI is built on Go using Cobra library. The version of Go used while creating the CLI is 1.6.
- Go **1.6**

For Windows you will need some Terminal emulator that support usage of Makefiles (e.g Cygwin).

# Building

The building of the CLI relies on **prepare.sh**, **github-checkout.sh**, and **golang-checkout.sh** scripts to setup the dependencies. 

```shell
cd ${path_to_admiral_project}/cli/src/
```

You can build it for specific OS or for all currently supported(Linux, Windows, OS X).

```
make #This will build binaries for all platforms both 32 and 64 bit versions.
make linux #This will build binaries for Linux both 32 and 64 bit versions.
make windows #This will build binaries for Windows both 32 and 64 bit versions.
make darwin #This will build binaries for OS X both 32 and 64 bit versions.
```

If you would like to contribute to this tool, you can use only **prepare.sh** to setup the dependencies and then build as it is your desired way to build Go applications, but then don't forget to setup properly the **GOPATH**. Using the script will get the needed dependencies and checkout to the right commit.
```
./prepare.sh
```

When the building is done, you should have the following directories and files:

```shell
admiral/  bin/  github.com/  github-checkout.sh  golang.org/  golang-checkout.sh  Makefile  prepare.sh
```
The binaries are inside the **bin** folder.
# Usage

Once you have built the tool, and have the binary for your platform, we recommend you to move it to place which is inside your $PATH or to include in $PATH the directory of the binary, then you can simply call it from everywhere.

```
admiral --version
```

When you use Admiral CLI for first time, it will create a config file inside `~/admiral-cli` directory. There will be stored also file containing the authorization token.

### Autocompletion

The Admiral CLI supports bash autocompletion thanks to Cobra library which is generating the needed script. You can generate it with the following command:
```
admiral autocomplete
```
The file will be placed again inside `~/admiral-cli`.
In order to use it, you can move it to `/etc/bash_completion.d/` then you should logout-login.

_Note: The generated script supports autocompletion for the current version of the CLI, that means if there are any new commands/features, you should generate this script again. You can also use another method to activate the script._

### Configuration and Authorization

The configuration file is in JSON format. In order to use the CLI you should configure the address where is running the Admiral. There are some ways to do it.
From the CLI, the following command will set in the configuration file the property with name _"url"_ the value _"http://127.0.0.1:8282"_, of course you can set another address where actually is running your Admiral.

```
admiral config set -k url -v http://127.0.0.1:8282
```

You can set the address when you login.

```
admiral login --url http://127.0.0.1:8282
```

The login is happening with the following command:

```
admiral login
```

You can pass the username and password as parameters.

```
admiral login --user Username --pass Password
```

In case you didn't pass them as parameters, you will be prompted to enter the missing field. By default, if you have set the property _"user"_ in the configuration file, it will automatically take it and will not prompt you for username. If the login is successful the authorazion token will be stored in the directory ```~/admiral-cli```. You can get more information about the currently used token.

```
admiral login --status
```

The CLI can use authorization token from the file, from environment variable ```ADMIRAL_TOKEN``` and also for each command you use, you can pass specific token with the parameter _"--token"_. The priority is **Parameter > Environment variable > File**.
Once you have setup the auhtorization and configuration you can start manage your Admiral with the CLI.

Don't forget that every command have _"--help"_ option which will display more information about it.