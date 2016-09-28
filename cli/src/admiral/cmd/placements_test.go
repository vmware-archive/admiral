package cmd

import (
	"admiral/config"
	"strings"
	"testing"
)

func TestPlacementAddRemove(t *testing.T) {
	testArguments := [][]string{}

	testArguments = append(
		testArguments, []string{"--resource-pool=default-resource-pool"})

	testArguments = append(
		testArguments, []string{"--resource-pool=default-resource-pool", "--priority=50",
			"--memoryLimit=100mb", "--instances=10", "--cpuShares=2", "--project=", "--deployment-policy="})

	// Preparing the test
	testPrintln("Configuring the env.")
	config.GetCfg()
	tc, err := configureTestEnv()
	CheckTestError(err, t)

	testPrintln("Login and adding host.")
	hostMsg := loginAndAddHost(tc, t)
	hostId := strings.Split(hostMsg, " ")[2]

	testPrintln("Adding new project.")
	projectAddCmd.ParseFlags([]string{"--description=test-description"})
	projectMsg, err := RunProjectAdd([]string{"test-project"})
	CheckTestError(err, t)
	projectId := strings.Split(projectMsg, " ")[2]

	testPrintln("Addning new deployment policy.")
	deploymentPolicyAddCmd.ParseFlags([]string{"--description=test-dp-description"})
	dpMsg, err := RunDeploymentPolicyAdd([]string{"test-deployment-policy"})
	CheckTestError(err, t)
	dpId := strings.Split(dpMsg, " ")[3]

	// Setting up IDs
	testArguments[1][5] += projectId
	testArguments[1][6] += dpId

	// Run the test.
	for i := range testArguments {
		testPrintln("Adding new placement.")
		resetFlagValues(placementAddCmd)
		placementAddCmd.ParseFlags(testArguments[i])
		placementMsg, err := RunPlacementAdd([]string{"test-placement"})
		CheckTestError(err, t)
		placementId := strings.Split(placementMsg, " ")[2]
		testPrintln("Removing the placement")
		placementMsg, err = RunPlacementRemove([]string{placementId})
		CheckTestError(err, t)
	}

	// Clean up the env.
	testPrintln("Removing the project.")
	projectMsg, err = RunProjectRemove([]string{projectId})
	CheckTestError(err, t)

	testPrintln("Removing the deployment policy.")
	dpMsg, err = RunDeploymentPolicyRemove([]string{dpId})
	CheckTestError(err, t)

	testPrintln("Removing the host.")
	hostMsg, err = RunHostRemove([]string{hostId})
	CheckTestError(err, t)
}
