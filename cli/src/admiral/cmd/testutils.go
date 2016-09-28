package cmd

import (
	"encoding/json"
	"fmt"
	"os"
	"testing"

	"admiral/credentials"

	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
)

type TestConfig struct {
	AdmiralAddress string `json:"admiralAddress"`
	Username       string `json:"username"`
	Password       string `json:"password"`
	PublicKey      string `json:"publicKey"`
	PrivateKey     string `json:"privateKey"`
	HostAddress    string `json:"hostAddress"`
	ResourcePool   string `json:"resourcePool"`
}

func configureTestEnv() (*TestConfig, error) {
	file, err := os.Open("../testdata/test.config")
	if err != nil {
		return nil, err
	}
	defer file.Close()
	tc := &TestConfig{}
	decoder := json.NewDecoder(file)
	err = decoder.Decode(tc)
	return tc, err
}

func clearCredentials() {
	lc := &credentials.ListCredentials{}
	lc.FetchCredentials()
	for _, cred := range lc.Documents {
		if scope, ok := cred.CustomProperties["scope"]; ok {
			if *scope == "SYSTEM" {
				continue
			}
		}
		credentials.RemoveCredentialsID(cred.GetID())
	}
}

func loginAndAddHost(tc *TestConfig, t *testing.T) string {
	err := loginCmd.ParseFlags([]string{"--user=" + tc.Username, "--pass=" + tc.Password, "--url=" + tc.AdmiralAddress})
	CheckTestError(err, t)
	token := RunLogin([]string{})
	if token == "" {
		t.Error("Login failed.")
		t.FailNow()
	}

	testPrintln("Removing host before add new one. Having error here is expected.")
	hostRemoveCmd.ParseFlags([]string{"--force"})
	RunHostRemove([]string{tc.HostAddress})

	testPrintln("Clearing credentials.")
	clearCredentials()

	testPrintln("Adding host.")
	hostAddCmd.ParseFlags([]string{"--ip=" + tc.HostAddress, "--resource-pool=" + tc.ResourcePool,
		"--public=" + tc.PublicKey, "--private=" + tc.PrivateKey, "--accept"})
	hostMsg, err := RunAddHost([]string{})
	CheckTestError(err, t)
	return hostMsg
}

func CheckTestError(err error, t *testing.T) {
	if err != nil {
		t.Error(err)
		t.FailNow()
	}
}

func testPrintln(s string) {
	output := "\x1b[31;1m----->" + s + "\x1b[37;1m"
	fmt.Println(output)
}

func resetFlagValues(c *cobra.Command) {
	c.Flags().VisitAll(func(f *pflag.Flag) {
		f.Value.Set(f.DefValue)
	})
}
