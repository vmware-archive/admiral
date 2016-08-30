package main

import (
	"io/ioutil"
	"strings"
	"testing"
)

func TestOnlyDefaultConfig(t *testing.T) {
	var internalConfigArrayFlags = &arrayFlags{}
	var publicConfigArrayFlags = &arrayFlags{}

	updateConfiguration(internalConfigArrayFlags, publicConfigArrayFlags, "*", "*")
	var config = readConfig()

	var hasFBConfig = strings.Contains(config, "frontend ") || strings.Contains(config, "backend ")

	if hasFBConfig {
		t.Errorf("Test failed, expected result config not to contain frontend or backend configuration. Config: %s", config)
	}
}

func TestInternalConfig(t *testing.T) {
	var internalConfigArrayFlags = &arrayFlags{}
	// Typical 3 node cluster configuration, accessible from container happy_knuth
	internalConfigArrayFlags.Set("happy_knuth:80:10.0.0.1:32782")
	internalConfigArrayFlags.Set("happy_knuth:80:10.0.0.2:32782")
	internalConfigArrayFlags.Set("happy_knuth:80:172.17.0.4:32782")

	// Configruation against 2 services, accessible from container jolly_shaw
	internalConfigArrayFlags.Set("jolly_shaw:80:10.0.10.10:32611")
	internalConfigArrayFlags.Set("jolly_shaw:81:10.0.10.10:32612")

	var publicConfigArrayFlags = &arrayFlags{}

	updateConfiguration(internalConfigArrayFlags, publicConfigArrayFlags, "172.17.0.1", "")
	var config = readConfig()

	var expectedFrontendConfig1 = "frontend internal_80\n" +
		"    bind 172.17.0.1:80\n" +
		"    use_backend %[src,map(/haproxy/containers.map,not_found)]_80\n"
	assertConfigurationExists(t, config, expectedFrontendConfig1)

	var expectedFrontendConfig2 = "frontend internal_81\n" +
		"    bind 172.17.0.1:81\n" +
		"    use_backend %[src,map(/haproxy/containers.map,not_found)]_81\n"
	assertConfigurationExists(t, config, expectedFrontendConfig2)

	var expectedBackendConfig1 = "backend happy_knuth_80\n" +
		"    server node-1 10.0.0.1:32782 check\n" +
		"    server node-2 10.0.0.2:32782 check\n" +
		"    server node-3 172.17.0.4:32782 check\n"
	assertConfigurationExists(t, config, expectedBackendConfig1)

	var expectedBackendConfig2 = "backend jolly_shaw_80\n" +
		"    server node-1 10.0.10.10:32611 check\n"
	assertConfigurationExists(t, config, expectedBackendConfig2)

	var expectedBackendConfig3 = "backend jolly_shaw_81\n" +
		"    server node-1 10.0.10.10:32612 check\n"
	assertConfigurationExists(t, config, expectedBackendConfig3)
}

func TestPublicConfig(t *testing.T) {
	var internalConfigArrayFlags = &arrayFlags{}

	var publicConfigArrayFlags = &arrayFlags{}
	publicConfigArrayFlags.Set("http:web.vmware.com:10.0.0.1:32782")
	publicConfigArrayFlags.Set("http:web.vmware.com:10.0.0.2:32782")
	publicConfigArrayFlags.Set("http:web.vmware.com:10.0.0.3:32782")
	publicConfigArrayFlags.Set("https:websecure.vmware.com:10.1.1.1:32782")

	updateConfiguration(internalConfigArrayFlags, publicConfigArrayFlags, "", "10.0.2.15")
	var config = readConfig()

	var expectedFrontendConfig1 = "frontend public_services\n" +
		"    bind 10.0.2.15:80\n" +
		"    acl host_web.vmware.com hdr_dom(host) -i web.vmware.com\n" +
		"    use_backend host_web.vmware.com if host_web.vmware.com\n"
	assertConfigurationExists(t, config, expectedFrontendConfig1)

	var expectedFrontendConfig2 = "frontend public_secure_services\n" +
		"    bind 10.0.2.15:443\n" +
		"    acl host_websecure.vmware.com req.ssl_sni -i websecure.vmware.com\n" +
		"    use_backend host_websecure.vmware.com if host_websecure.vmware.com\n"
	assertConfigurationExists(t, config, expectedFrontendConfig2)

	var expectedBackendConfig1 = "backend host_web.vmware.com\n" +
		"    server node-1 10.0.0.1:32782 check\n" +
		"    server node-2 10.0.0.2:32782 check\n" +
		"    server node-3 10.0.0.3:32782 check\n"
	assertConfigurationExists(t, config, expectedBackendConfig1)

	var expectedBackendConfig2 = "backend host_websecure.vmware.com\n" +
		"    server node-1 10.1.1.1:32782 check\n"
	assertConfigurationExists(t, config, expectedBackendConfig2)
}

func TestInternalConfigChange(t *testing.T) {
	var internalConfigArrayFlags = &arrayFlags{}
	internalConfigArrayFlags.Set("happy_knuth:80:10.0.0.1:32782")

	var publicConfigArrayFlags = &arrayFlags{}

	updateConfiguration(internalConfigArrayFlags, publicConfigArrayFlags, "172.17.0.1", "")
	var config = readConfig()

	var expectedFrontendConfig = "frontend internal_80\n" +
		"    bind 172.17.0.1:80\n" +
		"    use_backend %[src,map(/haproxy/containers.map,not_found)]_80\n"
	assertConfigurationExists(t, config, expectedFrontendConfig)

	var expectedBackendConfig = "backend happy_knuth_80\n" +
		"    server node-1 10.0.0.1:32782 check\n"
	assertConfigurationExists(t, config, expectedBackendConfig)

	var newInternalConfigArrayFlags = &arrayFlags{}
	newInternalConfigArrayFlags.Set("jolly_shaw:80:10.0.10.10:32611")

	updateConfiguration(newInternalConfigArrayFlags, publicConfigArrayFlags, "172.17.0.1", "")
	config = readConfig()

	expectedFrontendConfig = "frontend internal_80\n" +
		"    bind 172.17.0.1:80\n" +
		"    use_backend %[src,map(/haproxy/containers.map,not_found)]_80\n"
	assertConfigurationExists(t, config, expectedFrontendConfig)

	expectedBackendConfig = "backend jolly_shaw_80\n" +
		"    server node-1 10.0.10.10:32611 check\n"
	assertConfigurationExists(t, config, expectedBackendConfig)
}

func readConfig() string {
	var bytes, _ = ioutil.ReadFile("/haproxy/haproxy.cfg")
	return string(bytes)
}

func assertConfigurationExists(t *testing.T, fullConfig string, expectedConfig string) {
	lines := strings.Split(expectedConfig, "\n")
	for _, line := range lines {
		if !strings.Contains(fullConfig, line) {
			t.Errorf("Test failed, expected to contain config: '%s', got full config:  '%s'", line, fullConfig)
		}
	}

}
