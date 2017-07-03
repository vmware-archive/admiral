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
package main

import (
	"testing"
	"common"
	"text/template"
	"io/ioutil"
	"os"
	"log"
	"strings"
	"bytes"
	"fmt"
)

const testTemplateFile string = "test/haproxy_cfg.template"
const testExpectedHAConfigFile string = "test/haproxy_test.cfg"
const testHAConfigFile string = "generated_haproxy.cfg"

const testHealthCheckTemplateFile string = "test/haproxy_health_check_cfg.template"
const testExpectedHAHealthCheckConfigFile string = "test/haproxy_health_check_test.cfg"
const testHAHealthCheckConfigFile string = "generated_haproxy_health_check.cfg"

func TestExecuteHAproxyTemplate(t *testing.T) {

	var config = getTestConfig()
	var tmpl, err = template.ParseFiles(testTemplateFile)
	if err != nil {
		t.Error(err)
	}

	var configFile, configFileErr = os.OpenFile(testHAConfigFile, os.O_CREATE|os.O_WRONLY, 0766)
	if configFileErr != nil {
		log.Fatalf("Can not open test haproxy config file!", configFileErr)
	}
	defer configFile.Close()

	executeHAproxyTemplate(config, configFile, tmpl)

	var expected, err1 = ioutil.ReadFile(testExpectedHAConfigFile)
	if err1 != nil {
		t.Fatal(err1)
	}

	var actual, err2 = ioutil.ReadFile(testHAConfigFile)
	if err2 != nil {
		t.Fatal(err2)
	}

	var expectedTrimed []byte = []byte(strings.Join(strings.Fields(string(expected)),""))
	var actualTrimed []byte = []byte(strings.Join(strings.Fields(string(actual)),""))
	if !bytes.Equal(expectedTrimed, actualTrimed) {
		t.Errorf("Expected [%s] - Actual [%s]", expectedTrimed, actualTrimed)
	}
}

func TestExecuteHAproxyTemplateHealthCheck(t *testing.T) {

	var config = getTestHealthCheckConfig()
	var tmpl, err = template.ParseFiles(testHealthCheckTemplateFile)
	if err != nil {
		t.Error(err)
	}

	var configFile, configFileErr = os.OpenFile(testHAHealthCheckConfigFile, os.O_CREATE|os.O_WRONLY, 0766)
	if configFileErr != nil {
		log.Fatalf("Can not open test haproxy config file!", configFileErr)
	}
	defer configFile.Close()

	executeHAproxyTemplate(config, configFile, tmpl)

	var expected, err1 = ioutil.ReadFile(testExpectedHAHealthCheckConfigFile)
	if err1 != nil {
		t.Fatal(err1)
	}

	var actual, err2 = ioutil.ReadFile(testHAHealthCheckConfigFile)
	if err2 != nil {
		t.Fatal(err2)
	}

	fmt.Printf(string(actual))
	var expectedTrimed []byte = []byte(strings.Join(strings.Fields(string(expected)),""))
	var actualTrimed []byte = []byte(strings.Join(strings.Fields(string(actual)),""))
	if !bytes.Equal(expectedTrimed, actualTrimed) {
		t.Errorf("Expected [%s] - Actual [%s]", expectedTrimed, actualTrimed)
	}
}

func TestUpdateHAProxy(t *testing.T) {

	var config = getTestConfig()

	updateHAProxy(config, testHAConfigFile, testTemplateFile)

	var expected, err1 = ioutil.ReadFile(testExpectedHAConfigFile)
	if err1 != nil {
		t.Fatal(err1)
	}

	var actual, err2 = ioutil.ReadFile(testHAConfigFile)
	if err2 != nil {
		t.Fatal(err2)
	}

	var expectedTrimed []byte = []byte(strings.Join(strings.Fields(string(expected)),""))
	var actualTrimed []byte = []byte(strings.Join(strings.Fields(string(actual)),""))
	if !bytes.Equal(expectedTrimed, actualTrimed) {
		t.Errorf("Expected [%s] - Actual [%s]", expectedTrimed, actualTrimed)
	}
}

func getTestConfig() *common.Config{
	var config = common.Config{}
	config.Frontends = make([]common.Frontend, 3, 3)

	config.Frontends[0].Port = 333
	config.Frontends[0].Backends = make([]common.Backend,1,1)
	config.Frontends[0].Backends[0] = common.Backend{}
	config.Frontends[0].Backends[0].Port = 3333
	config.Frontends[0].Backends[0].Host = "www.four-three.com"

	config.Frontends[1].Port = 4444
	config.Frontends[1].Backends = make([]common.Backend,2,2)
	config.Frontends[1].Backends[0] = common.Backend{}
	config.Frontends[1].Backends[0].Port = 4433
	config.Frontends[1].Backends[0].Host = "www.four-four-three-three.org"
	config.Frontends[1].Backends[1] = common.Backend{}
	config.Frontends[1].Backends[1].Port = 555
	config.Frontends[1].Backends[1].Host = "www.three-five.net"

	config.Frontends[2].Port = 5555
	config.Frontends[2].Backends = make([]common.Backend,3,3)
	config.Frontends[2].Backends[0] = common.Backend{}
	config.Frontends[2].Backends[0].Port = 777
	config.Frontends[2].Backends[0].Host = "www.seven-seven-seven.org"
	config.Frontends[2].Backends[1] = common.Backend{}
	config.Frontends[2].Backends[1].Port = 333
	config.Frontends[2].Backends[1].Host = "www.three-three.com"
	config.Frontends[2].Backends[2] = common.Backend{}
	config.Frontends[2].Backends[2].Port = 2222
	config.Frontends[2].Backends[2].Host = "www.four-two.net"

	return &config
}



func getTestHealthCheckConfig() *common.Config{
	var config = common.Config{}
	config.Frontends = make([]common.Frontend, 3, 3)

	config.Frontends[0].Port = 333
	config.Frontends[0].Backends = make([]common.Backend,1,1)
	config.Frontends[0].Backends[0] = common.Backend{}
	config.Frontends[0].Backends[0].Port = 3333
	config.Frontends[0].Backends[0].Host = "www.four-three.com"

	config.Frontends[1].Port = 4444
	config.Frontends[1].Backends = make([]common.Backend,2,2)
	config.Frontends[1].Backends[0] = common.Backend{}
	config.Frontends[1].Backends[0].Port = 4433
	config.Frontends[1].Backends[0].Host = "www.four-four-three-three.org"
	config.Frontends[1].Backends[1] = common.Backend{}
	config.Frontends[1].Backends[1].Port = 555
	config.Frontends[1].Backends[1].Host = "www.three-five.net"
	config.Frontends[1].HealthConfig = common.HealthConfig{}
	config.Frontends[1].HealthConfig.Port = 8080
	config.Frontends[1].HealthConfig.Path = "/health_check"
	config.Frontends[1].HealthConfig.Protocol = "http"

	config.Frontends[2].Port = 5555
	config.Frontends[2].Backends = make([]common.Backend,3,3)
	config.Frontends[2].Backends[0] = common.Backend{}
	config.Frontends[2].Backends[0].Port = 777
	config.Frontends[2].Backends[0].Host = "www.seven-seven-seven.org"
	config.Frontends[2].Backends[1] = common.Backend{}
	config.Frontends[2].Backends[1].Port = 333
	config.Frontends[2].Backends[1].Host = "www.three-three.com"
	config.Frontends[2].Backends[2] = common.Backend{}
	config.Frontends[2].Backends[2].Port = 2222
	config.Frontends[2].Backends[2].Host = "www.four-two.net"
	config.Frontends[2].HealthConfig.Port = 8443
	config.Frontends[2].HealthConfig.Path = "/status"
	config.Frontends[2].HealthConfig.Protocol = "https"

	return &config
}
