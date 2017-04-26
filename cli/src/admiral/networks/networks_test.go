// +build integration

/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package networks

import (
	"fmt"
	"os"
	"testing"

	"admiral/auth"
	. "admiral/common/utils"
	"admiral/config"
	"admiral/credentials"
	"admiral/hosts"
)

var tc = &TestConfig{}

func TestMain(m *testing.M) {
	var err error
	tc, err = ConfigureTestEnv()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	IsTest = true
	config.GetCfgForTests()
	auth.Login(tc.Username, tc.Password, tc.AdmiralAddress)

	code := m.Run()
	os.Exit(code)
}

func TestAddRemoveNetwork(t *testing.T) {
	// Preparing
	credentialsID, err := credentials.AddByCert("test-credentials", tc.PublicKey, tc.PrivateKey, nil)
	CheckTestError(err, t)
	hostID, err := hosts.AddHost(tc.HostAddress, tc.PlacementZone, "", credentialsID, "", "", "", "", true, nil, nil)
	CheckTestError(err, t)

	networkName := "test-network"
	networkGateway := "172.16.238.1"
	networkSubnet := "172.16.238.0/24"
	networkIPRange := "172.28.5.0/24"

	// Testing phase 1
	networkId, err := CreateNetwork(networkName, "", "",
		[]string{networkGateway}, []string{networkSubnet}, []string{networkIPRange}, nil, []string{hostID}, false)
	CheckTestError(err, t)

	// Validating phase 1
	nl := NetworkList{}
	_, err = nl.FetchNetworks()
	CheckTestError(err, t)
	exist := false
	actualNetwork := Network{}
	for _, n := range nl.Documents {
		if n.GetID() == networkId {
			exist = true
			actualNetwork = n
			break
		}
	}

	if !exist {
		t.Error("Created network not found.")
	}

	if len(actualNetwork.IPAM.IPAMConfigs) != 1 {
		t.Errorf("Expected count of IPAMConfigs: %d, actual count of IPAMConfigs: %d",
			1, len(actualNetwork.IPAM.IPAMConfigs))
		teardown(hostID, credentialsID, networkId, t)
		t.FailNow()
	}

	actualIPAMconfig := actualNetwork.IPAM.IPAMConfigs[0]

	if actualIPAMconfig.Gateway != networkGateway {
		t.Errorf("Expected ipam gateway: %s, actual ipam gateway: %s",
			networkGateway, actualIPAMconfig.Gateway)
	}

	if actualIPAMconfig.IPRange != networkIPRange {
		t.Errorf("Expected ipam ip range: %s, actual ipam ip range: %s",
			networkGateway, actualIPAMconfig.IPRange)
	}

	if actualIPAMconfig.Subnet != networkSubnet {
		t.Errorf("Expected ipam subnet: %s, actual ipam subnet: %s",
			networkSubnet, actualIPAMconfig.Subnet)
	}

	// Testing phase 2
	networkIds, err := RemoveNetwork([]string{networkId}, false)
	CheckTestError(err, t)

	// Validating phase 2
	if len(networkIds) != 1 {
		t.Errorf("Expected count of returned IDs after remove %s, actual count of returned IDs after remove: %s",
			1, len(networkIds))
		teardown(hostID, credentialsID, "", t)
		t.FailNow()
	}

	nl = NetworkList{}
	_, err = nl.FetchNetworks()
	CheckTestError(err, t)
	exist = false
	for _, n := range nl.Documents {
		if n.GetID() == networkIds[0] {
			exist = true
			break
		}
	}

	if exist {
		t.Error("Removed network is found.")
	}

	// Cleaning
	teardown(hostID, credentialsID, "", t)
}

func teardown(hostId, credentialsId, networkId string, t *testing.T) {
	if networkId != "" {
		_, err := RemoveNetwork([]string{networkId}, false)
		CheckTestError(err, t)
	}
	_, err := hosts.RemoveHost(hostId, false)
	CheckTestError(err, t)
	err = hosts.ValidateHostIsDeleted(hostId)
	CheckTestError(err, t)
	_, err = credentials.RemoveCredentialsID(credentialsId)
	CheckTestError(err, t)
}
