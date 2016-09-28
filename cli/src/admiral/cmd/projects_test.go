package cmd

import (
	"admiral/config"
	"strings"
	"testing"
)

func TestAddUseRemoveProjects(t *testing.T) {
	// Preparing the test.
	testPrintln("Configuring the env.")
	config.GetCfg()
	tc, err := configureTestEnv()
	CheckTestError(err, t)

	testPrintln("Login and adding host.")
	hostMsg := loginAndAddHost(tc, t)
	hostId := strings.Split(hostMsg, " ")[2]

	// Run the test
	testPrintln("Adding new project.")
	projectAddCmd.ParseFlags([]string{"--description=test-description"})
	projectMsg, err := RunProjectAdd([]string{"test-project"})
	CheckTestError(err, t)
	projectId := strings.Split(projectMsg, " ")[2]

	testPrintln("Provisioning image with the new project.")
	containerRunCmd.ParseFlags([]string{"--project=" + projectId})
	contMsg, err := RunContainerRun([]string{"kitematic/hello-world-nginx"})
	CheckTestError(err, t)
	contId := strings.Split(contMsg, " ")[2]

	testPrintln("Removing the provisioned container.")
	contMsg, err = RunContainersRemove([]string{contId})
	CheckTestError(err, t)

	testPrintln("Updating the project.")
	projectUpdateCmd.ParseFlags([]string{"--name=test-test-project", "--description==test-test-description"})
	projectMsg, err = RunProjectUpdate([]string{projectId})
	CheckTestError(err, t)

	testPrintln("Removing the project.")
	projectMsg, err = RunProjectRemove([]string{projectId})
	CheckTestError(err, t)

	// Clean up the env.
	testPrintln("Removing the host.")
	hostMsg, err = RunHostRemove([]string{hostId})
	CheckTestError(err, t)
}
