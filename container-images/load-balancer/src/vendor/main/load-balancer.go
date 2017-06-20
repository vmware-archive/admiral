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
	"common"
	"os"
	"text/template"
	"log"
	"fmt"
	"io/ioutil"
)

const templateFileStr string = "/etc/haproxy/haproxy.cfg.tmpl"
const configFileStr string = "/etc/haproxy/haproxy.cfg"

func main() {

	var cfg, errRead = common.ReadFromInput(os.Stdin, "Enter load balancer configuration as json: ")
	if errRead != nil {
		log.Fatalf("Can not read ha proxy configuration from input: %v", errRead)
	}

    var tmpl, err = template.ParseFiles(templateFileStr)
	if err != nil {
		log.Fatalf("Can not parse template file!: %v", err)
	}

	var configFile, configFileErr = os.OpenFile(configFileStr, os.O_CREATE|os.O_RDWR, 0777)
	if configFileErr != nil {
		log.Fatalf("Can not open HA proxy configuration file!: %v", configFileErr)
	}


	updateHAProxy(cfg, configFile, tmpl)
	configFile.Close()

	var fileContent, testErr = ioutil.ReadFile(configFileStr);
	if testErr != nil {
		log.Println(testErr)
	}
	fmt.Println(cfg)
	fmt.Println(string(fileContent))
}

func updateHAProxy(services *common.Config, configFile *os.File, templateFile *template.Template) {
	var err = templateFile.Execute(configFile, services)
	if err != nil {
		log.Fatalf("Error applying services over template!", err)
	}
}


