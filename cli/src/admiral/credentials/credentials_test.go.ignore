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

package credentials

import (
	"fmt"
	"os"
	"testing"

	"admiral/auth"
	. "admiral/common/utils"
	"admiral/config"
	"admiral/placement_zones"
	"admiral/placements"
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
	placement_zones.BuildDefaultPlacementZone()
	placements.BuildDefaultPlacement()
	code := m.Run()
	os.Exit(code)
}

func TestAddRemoveCredentials(t *testing.T) {
	// Preparing
	name1 := "test-name-byUsername"
	name2 := "test-name-byCertificates"
	username := "testuser"
	password := "testpassword"
	publicCert := "fake-public-cert"
	privateCert := "fake-private-cert"
	publicCertPath := "../testdata/cert.pem"
	privateCertPath := "../testdata/key.pem"

	// Testing phase 1 (Adding credentials with username)
	idByUsername, err := AddByUsername(name1, username, password, nil)
	CheckTestError(err, t)

	// Testing phase 2 (Adding credentials with certificates)
	idByCert, err := AddByCert(name2, publicCertPath, privateCertPath, nil)
	CheckTestError(err, t)

	// Validating phase 1 & phase 2
	lc := &CredentialsList{}
	lc.FetchCredentials()
	existByUsername := false
	existByCert := false
	actualByCert := Credentials{}
	actualByUsername := Credentials{}
	for _, cred := range lc.Documents {
		if cred.GetID() == idByCert {
			existByCert = true
			actualByCert = cred
		} else if cred.GetID() == idByUsername {
			existByUsername = true
			actualByUsername = cred
		}
	}

	if !existByUsername {
		t.Error("Credentials added with username are not found.")
	}

	if !existByCert {
		t.Error("Credentials added with certificates are not found.")
	}

	if actualByCert.GetName() != name2 {
		t.Errorf("Expected name: %s, actual name: %s", name2, actualByCert.GetName())
	}

	if actualByUsername.GetName() != name1 {
		t.Errorf("Expected name: %s, actual name: %s", name1, actualByUsername.GetName())
	}

	if actualByCert.PublicKey != publicCert || actualByCert.PrivateKey != privateCert {
		t.Errorf("Expected public key: %s, actual public key: %s. Expected private key: %s, actual private key: %s",
			publicCert, actualByCert.PublicKey, privateCert, actualByCert.PrivateKey)
	}

	if actualByUsername.UserEmail != username || actualByUsername.PrivateKey != password {
		t.Errorf("Expected username: %s, actual username: %s. Expected password: %s, actual password: %s",
			username, actualByUsername.UserEmail, password, actualByUsername.PrivateKey)
	}

	// Testing phase 3 (Removing both credentials)
	idByUsername, err = RemoveCredentialsID(idByUsername)
	CheckTestError(err, t)
	idByCert, err = RemoveCredentialsID(idByCert)
	CheckTestError(err, t)

	// Validating phase 3
	lc = &CredentialsList{}
	lc.FetchCredentials()
	existByUsername = false
	existByCert = false
	for _, cred := range lc.Documents {
		if cred.GetID() == idByCert {
			existByCert = true
		} else if cred.GetID() == idByUsername {
			existByUsername = true
		}
	}

	if existByUsername {
		t.Error("Credentials removed with username are found.")
	}

	if existByCert {
		t.Error("Credentials removed with certificates are found.")
	}
}

func TestUpdateCredentials(t *testing.T) {
	// Preparing
	name := "test-name"
	username := "testuser"
	newUsername := "new-testuser"
	password := "testpassword"
	newPassword := "new-testpassword"
	id, err := AddByUsername(name, username, password, nil)
	CheckTestError(err, t)

	// Testing
	id, err = EditCredetialsID(id, "", "", newUsername, newPassword)
	CheckTestError(err, t)

	// Validating
	lc := &CredentialsList{}
	lc.FetchCredentials()
	exist := false
	actual := Credentials{}
	for _, cred := range lc.Documents {
		if cred.GetID() == id {
			exist = true
			actual = cred
		}
	}

	if !exist {
		t.Error("Credentials added are not found.")
	}

	if actual.UserEmail != newUsername || actual.PrivateKey != newPassword {
		t.Errorf("Expected username: %s, actual username: %s. Expected password: %s, actual password: %s",
			newUsername, actual.UserEmail, newPassword, actual.PrivateKey)
	}

	//Cleaning
	_, err = RemoveCredentialsID(id)
	CheckTestError(err, t)
}

func TestAddRemoveCustomPropertiesOfCredentials(t *testing.T) {

}

func TestUpdateCustomPropertiesOfCredentials(t *testing.T) {
	// Preparing
	name1 := "test-name-byUsername"
	username := "testuser"
	password := "testpassword"
	id, err := AddByUsername(name1, username, password, nil)
	CheckTestError(err, t)
	cpKeys := []string{"key1", "key2", "key3"}
	cpVals := []string{"val1", "val2", "val3"}
	cpNewVals := []string{"new-val1", "new-val2", "new-val3"}
	err = AddCustomProperties(id, cpKeys, cpVals)
	CheckTestError(err, t)

	// Testing
	err = AddCustomProperties(id, cpKeys, cpNewVals)
	CheckTestError(err, t)

	// Validating
	customProps, err := GetPublicCustomProperties(id)
	for i := range cpKeys {
		if val, ok := customProps[cpKeys[i]]; !ok {
			t.Errorf("%s key is missing.", cpKeys[i])
		} else {
			if val != nil && *val != cpNewVals[i] {
				t.Errorf("Expected value: %s, actual value: %s", val, cpNewVals[i])
			}
		}
	}

	// Cleaning
	_, err = RemoveCredentialsID(id)
	CheckTestError(err, t)
}

func TestAddRemoveCustomPropertiesOfResourcePool(t *testing.T) {
	// Preparing
	name1 := "test-name-byUsername"
	username := "testuser"
	password := "testpassword"
	id, err := AddByUsername(name1, username, password, nil)
	CheckTestError(err, t)

	// Testing phase 1
	cpKeys := []string{"key1", "key2", "key3"}
	cpVals := []string{"val1", "val2", "val3"}
	err = AddCustomProperties(id, cpKeys, cpVals)
	CheckTestError(err, t)

	// Validating phase 1
	customProps, err := GetPublicCustomProperties(id)
	for i := range cpKeys {
		if val, ok := customProps[cpKeys[i]]; !ok {
			t.Errorf("%s key is missing.", cpKeys[i])
		} else {
			if val != nil && *val != cpVals[i] {
				t.Errorf("Expected value: %s, actual value: %s", val, cpVals[i])
			}
		}
	}

	// Testing phase 2
	err = RemoveCustomProperties(id, cpKeys)
	CheckTestError(err, t)

	// Validating phase 2
	customProps, err = GetPublicCustomProperties(id)
	for i := range cpKeys {
		if _, ok := customProps[cpKeys[i]]; ok {
			t.Errorf("%s key has not been removed.", cpKeys[i])
		}
	}
	// Cleaning
	_, err = RemoveCredentialsID(id)
	CheckTestError(err, t)
}
