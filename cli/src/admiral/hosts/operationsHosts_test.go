package hosts

import (
	"fmt"
	"testing"

	"admiral/certificates"
	"admiral/config"
	"admiral/credentials"
	"admiral/functions"
)

var (
	ip             = ""
	cred           = ""
	publicPath     = ""
	privatePath    = ""
	certIssuerName = ""
)

func preparation(ip, cred string) {
	fmt.Println("Preparing for test...")
	config.GetCfg()
	functions.Verbose = false
	hl := &HostsList{}
	count := hl.FetchHosts(ip)
	if count > 0 {
		RemoveHost(ip, true)
	}
	credentials.RemoveCredentials(cred)
	//functions.Verbose = true
	fmt.Println("Preparing finished. Staring test.")
}

func TestAddRemoveHost(t *testing.T) {
	preparation(ip, cred)
	expectedRes := true
	actualRes, _ := AddHost(ip, "default-resource-pool", cred,
		publicPath,
		privatePath, "", "", true)
	hl := HostsList{}
	expectedCount := 1
	actualCount := hl.FetchHosts("")
	if expectedRes != actualRes || expectedCount != actualCount {
		t.Error("Error on try to add host.")
	}
	actualRes = RemoveHost(ip, true)
	actualCount = hl.FetchHosts("")
	expectedCount = 0
	if expectedRes != actualRes || expectedCount != actualCount {
		t.Error("Error on try to remove host.")
	}

}

func TestAddHostAndCertificate(t *testing.T) {
	functions.Verbose = true
	preparation(ip, cred)
	certificates.RemoveCertificate(certIssuerName)
	expectedRes := true
	actualRes, _ := AddHost(ip, "default-resource-pool", cred, publicPath, privatePath, "", "", true)
	hl := HostsList{}
	expectedCount := 1
	actualCount := hl.FetchHosts("")
	if expectedRes != actualRes || expectedCount != actualCount {
		t.Error("Error on try to add host.")
	}

}
