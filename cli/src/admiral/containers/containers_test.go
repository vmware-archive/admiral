// +build integration

package containers

import (
	"fmt"
	"os"
	"testing"

	"admiral/config"
	"admiral/credentials"
	"admiral/hosts"
	"admiral/loginout"
	"admiral/nulls"
	. "admiral/testutils"
)

var tc = &TestConfig{}

func TestMain(m *testing.M) {
	var err error
	tc, err = ConfigureTestEnv()
	if err != nil {
		fmt.Println(err)
		os.Exit(-1)
	}
	config.GetCfg()
	loginout.Login(tc.Username, tc.Password, tc.AdmiralAddress)

	code := m.Run()

	os.Exit(code)
}

func TestProvisionRemoveContainer(t *testing.T) {
	// Preparation
	credentialsID, err := credentials.AddByCert("test-credentials", tc.PublicKey, tc.PrivateKey, nil)
	CheckTestError(err, t)
	hostID, err := hosts.AddHost(tc.HostAddress, tc.PlacementZone, "", credentialsID, "", "", "", "", true, nil)
	CheckTestError(err, t)
	containerName := "ubuntu"
	imageName := "ubuntu"
	cd := ContainerDescription{
		Name:  nulls.NilString{containerName},
		Image: nulls.NilString{imageName},
	}

	// Testing phase 1
	contId, err := cd.RunContainer("", false)
	CheckTestError(err, t)

	// Validating phase 1
	lc := ListContainers{}
	lc.FetchContainers("")
	exist := false
	for _, cont := range lc.Documents {
		if cont.GetID() == contId {
			exist = true
			break
		}
	}

	if !exist {
		t.Error("Provisioned container is not found.")
	}

	// Testing phase 2
	contIds, err := RemoveContainer([]string{contId}, false)
	CheckTestError(err, t)

	// Validating phase 2
	lc = ListContainers{}
	lc.FetchContainers("")
	exist = false
	for _, cont := range lc.Documents {
		if cont.GetID() == contIds[0] {
			exist = true
			break
		}
	}

	if exist {
		t.Error("Removed container is found.")
	}

	// Cleaning
	_, err = hosts.RemoveHost(hostID, false)
	CheckTestError(err, t)
	_, err = credentials.RemoveCredentialsID(credentialsID)
	CheckTestError(err, t)
}

func TestStopStartContainer(t *testing.T) {
	// Preparation
	credentialsID, err := credentials.AddByCert("test-credentials", tc.PublicKey, tc.PrivateKey, nil)
	CheckTestError(err, t)
	hostID, err := hosts.AddHost(tc.HostAddress, tc.PlacementZone, "", credentialsID, "", "", "", "", true, nil)
	CheckTestError(err, t)
	containerName := "ubuntu"
	imageName := "ubuntu"
	cd := ContainerDescription{
		Name:  nulls.NilString{containerName},
		Image: nulls.NilString{imageName},
	}
	contId, err := cd.RunContainer("", false)
	CheckTestError(err, t)

	// Testing phase 1
	contIds, err := StopContainer([]string{contId}, false)
	CheckTestError(err, t)

	// Validating phase 1
	lc := ListContainers{}
	lc.FetchContainers("")
	exist := false
	for _, cont := range lc.Documents {
		if cont.GetID() == contIds[0] {
			if cont.PowerState != "STOPPED" {
				t.Errorf("Expected container state: STOPPED, actual container state: %s", cont.PowerState)
			}
			exist = true
			break
		}
	}

	if !exist {
		t.Error("Provisioned container is not found.")
	}

	// Testing phase 2
	contIds, err = StartContainer([]string{contId}, false)
	CheckTestError(err, t)

	// Validating phase 2
	lc = ListContainers{}
	lc.FetchContainers("")
	for _, cont := range lc.Documents {
		if cont.GetID() == contIds[0] {
			if cont.PowerState != "RUNNING" {
				t.Errorf("Expected container state: RUNNING, actual container state: %s", cont.PowerState)
			}
			break
		}
	}

	// Cleaning
	_, err = RemoveContainer([]string{contId}, false)
	CheckTestError(err, t)
	_, err = hosts.RemoveHost(hostID, false)
	CheckTestError(err, t)
	_, err = credentials.RemoveCredentialsID(credentialsID)
	CheckTestError(err, t)
}
