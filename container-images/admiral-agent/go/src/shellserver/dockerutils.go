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

package main

import (
	"github.com/fsouza/go-dockerclient"
)

var endpoint = "unix:///var/run/docker.sock"
var client, _ = docker.NewClient(endpoint)

func IsContainerRunning(containerNameOrId string) (bool, error) {
	var container, err = client.InspectContainer(containerNameOrId)
	if err != nil {
		return false, err
	}

	return container.State.Running, nil
}

func GetContainerId(containerNameOrId string) (string, error) {
	var container, err = client.InspectContainer(containerNameOrId)
	if err != nil {
		return "", err
	}
	return container.ID, nil
}
