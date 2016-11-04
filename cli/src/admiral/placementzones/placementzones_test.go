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

package placementzones

import (
	"fmt"
	"os"
	"testing"

	"admiral/config"
	"admiral/loginout"
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

func TestAddRemovePlacementZone(t *testing.T) {
	// Testing phase 1
	name := "test-placement-zone"
	id, err := AddPZ(name, nil)
	CheckTestError(err, t)

	// Validating phase 1
	pzl := &PlacementZoneList{}
	_, err = pzl.FetchPZ()
	actualPZ := PlacementZone{}
	exist := false
	for _, rp := range pzl.Documents {
		if rp.ResourcePoolState.GetID() == id {
			exist = true
			actualPZ = rp
			break
		}
	}

	if !exist {
		t.Error("Added placement zone is not found.")
	}

	if actualPZ.ResourcePoolState.Name != name {
		t.Errorf("Expected name: %s, actual name: %s", name, actualPZ.ResourcePoolState.Name)
	}

	// Testing phase 2
	id, err = RemovePZID(id)
	CheckTestError(err, t)

	// Validating phase 2
	pzl = &PlacementZoneList{}
	_, err = pzl.FetchPZ()
	exist = false
	for _, rp := range pzl.Documents {
		if rp.ResourcePoolState.GetID() == id {
			exist = true
			break
		}
	}

	if exist {
		t.Error("Removed placement zone is found.")
	}
}

func TestUpdatePlacementZone(t *testing.T) {
	// Preparing
	name := "test-placement-zone"
	id, err := AddPZ(name, nil)
	CheckTestError(err, t)

	// Testing
	newName := "new-test-placement-zone"
	id, err = EditPZID(id, newName)
	CheckTestError(err, t)

	// Validating
	pzl := &PlacementZoneList{}
	_, err = pzl.FetchPZ()
	actualPZ := PlacementZone{}
	exist := false
	for _, pz := range pzl.Documents {
		if pz.ResourcePoolState.GetID() == id {
			exist = true
			actualPZ = pz
			break
		}
	}

	if !exist {
		t.Error("Added placement zone is not found.")
	}

	if actualPZ.ResourcePoolState.Name != newName {
		t.Errorf("Expected new name: %s, actual new name: %s", newName, actualPZ.ResourcePoolState.Name)
	}

	// Cleaning
	id, err = RemovePZID(id)
	CheckTestError(err, t)
}

func TestAddPlacementZoneWithEmptyName(t *testing.T) {
	// Testing
	name := ""
	_, err := AddPZ(name, nil)

	// Validating
	if err == nil {
		t.Error("Expected error != nil, got error = nil.")
	}
}

// Disabled for now!
//func TestAddRemoveCustomPropertiesOfPlacementZone(t *testing.T) {
//	// Required skip.
//	t.SkipNow()
//	// Preparing
//	name := "test-placement-zone"
//	id, err := AddPZ(name, nil)
//	CheckTestError(err, t)
//
//	// Testing phase 1
//	cpKeys := []string{"key1", "key2", "key3"}
//	cpVals := []string{"val1", "val2", "val3"}
//	err = AddCustomProperties(id, cpKeys, cpVals)
//	CheckTestError(err, t)
//
//	// Validating phase 1
//	customProps, err := GetPublicCustomProperties(id)
//	for i := range cpKeys {
//		if val, ok := customProps[cpKeys[i]]; !ok {
//			t.Errorf("%s key is missing.", cpKeys[i])
//		} else {
//			if val != cpVals[i] {
//				t.Errorf("Expected value: %s, actual value: %s", val, cpVals[i])
//			}
//		}
//	}
//
//	// Testing phase 2
//	err = RemoveCustomProperties(id, cpKeys)
//	CheckTestError(err, t)
//
//	// Validating phase 2
//	customProps, err = GetPublicCustomProperties(id)
//	for i := range cpKeys {
//		if _, ok := customProps[cpKeys[i]]; ok {
//			t.Errorf("%s key has not been removed.", cpKeys[i])
//		}
//	}
//	// Cleaning
//	_, err = RemovePZID(id)
//	CheckTestError(err, t)
//}
// Disabled for now!
//func TestUpdateCustomPropertiesOfPlacementZone(t *testing.T) {
//	// Required skip.
//	t.SkipNow()
//	// Preparing
//	name := "test-placement-zone"
//	id, err := AddPZ(name, nil)
//	CheckTestError(err, t)
//	cpKeys := []string{"key1", "key2", "key3"}
//	cpVals := []string{"val1", "val2", "val3"}
//	cpNewVals := []string{"new-val1", "new-val2", "new-val3"}
//	err = AddCustomProperties(id, cpKeys, cpVals)
//	CheckTestError(err, t)
//
//	// Testing
//	err = AddCustomProperties(id, cpKeys, cpNewVals)
//	CheckTestError(err, t)
//
//	// Validating
//	customProps, err := GetPublicCustomProperties(id)
//	for i := range cpKeys {
//		if val, ok := customProps[cpKeys[i]]; !ok {
//			t.Errorf("%s key is missing.", cpKeys[i])
//		} else {
//			if val != cpNewVals[i] {
//				t.Errorf("Expected value: %s, actual value: %s", val, cpNewVals[i])
//			}
//		}
//	}
//
//	// Cleaning
//	_, err = RemovePZID(id)
//	CheckTestError(err, t)
//
//}
