/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */
package common

import (
	"encoding/json"
	"io"
)

type Config struct {
	Frontends []Frontend `json:"frontends"` // array of services, defined as Frontend object
}

type Frontend struct {
	Port      int             `json:"port"`           // the frontend port
	Backends  []Backend       `json:"backends"`       // array of service's backends
	HealthConfig HealthConfig `json:"health_config"`  // backend service port
}

type Backend struct {
	Host string    `json:"host"`   // backend service hostname/IP
	Port int       `json:"port"`   // backend service port
}

type HealthConfig struct {
	Path string     `json:"path"`      // backend service health check path
	Protocol string `json:"protocol"`  // backend service health check protocol
	Port int        `json:"port"`      // backend service health check port
}

func ReadFromInput(r io.Reader) (*Config, error) {
	decoder := json.NewDecoder(r)
	var config Config
	if err := decoder.Decode(&config); err != nil {
		return nil, err
	}
	return &config, nil
}
