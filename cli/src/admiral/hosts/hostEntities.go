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

package hosts

import "admiral/credentials"

//Struct part of "Host" struct in order to parse inner data.
type HostProperties struct {
	Containers string `json:"__Containers"`
	Name       string `json:"__Name"`
}

//Struct part of "ListHosts" struct in order to parse inner data.
type Host struct {
	Id               string             `json:"id,omitempty"`
	Address          string             `json:"Address,omitempty"`
	PowerState       string             `json:"powerState,omitempty"`
	CustomProperties map[string]*string `json:"customProperties"`
}

//Struct to parse data when getting information about existing hosts.
type HostsList struct {
	TotalCount    int32           `json:"totalCount"`
	Documents     map[string]Host `json:"documents"`
	DocumentLinks []string        `json:"documentLinks"`
}

//Struct used to send data in order to change host's power state.
type HostPatch struct {
	PowerState string `json:"powerState"`
}

//Struct used to send needed data when creating host.
type HostState struct {
	Id               string             `json:"id"`
	Address          string             `json:"address"`
	ResourcePoolLink string             `json:"resourcePoolLink"`
	CustomProperties map[string]*string `json:"customProperties"`
}

//Struct used as wrapper of HostState for valid request.
type HostObj struct {
	HostState HostState `json:"hostState"`
}

type HostUpdate struct {
	Credential       credentials.Credentials `json:"credential,omitempty"`
	ResourcePoolLink string                  `json:"resourcePoolLink,omitempty"`
	CustomProperties map[string]*string      `json:"customProperties"`
}
