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
	"flag"
	"strings"
)

const templateFileStr string = "/etc/haproxy/haproxy.cfg.tmpl"
const configFileStr string = "/etc/haproxy/haproxy.cfg"

func main() {

	var jsonConfig = flag.String("config", "", "HA Proxy configuration in JSON format")
	flag.Parse()

	if *jsonConfig == "" {
		flag.PrintDefaults()
		os.Exit(1)
	}
	fmt.Printf("New configuration: [%s]\n", *jsonConfig)

	var cfg, errRead = common.ReadFromInput(strings.NewReader(*jsonConfig))
	if errRead != nil {
		log.Fatalf("Can not read ha proxy configuration from input: %v", errRead)
	}

	updateHAProxy(cfg, configFileStr, templateFileStr)

	var fileContent, testErr = ioutil.ReadFile(configFileStr)
	if testErr != nil {
		log.Println(testErr)
	}
	fmt.Printf("Generated HA proxy config file:\n%s\n", string(fileContent))
}

func updateHAProxy(services *common.Config, strConfigFile string, strTemplateFile string) {
	var tmpl, err = template.ParseFiles(strTemplateFile)
	if err != nil {
		log.Fatalf("Can not parse template file!: %v", err)
	}

	var configFile, configFileErr = os.OpenFile(strConfigFile, os.O_RDWR, 0777)
	if configFileErr != nil {
		log.Fatalf("Can not open HA proxy configuration file!: %v", configFileErr)
	}

	//Cleanup the old content of haproxy config file
	var truncateErr = configFile.Truncate(0)
	if truncateErr != nil {
		log.Fatalf("Error cleaning old content of file %s:  %v", strConfigFile, truncateErr)
	}

	//Execute template file, using common.Config configuration and
	executeHAproxyTemplate(services, configFile, tmpl)
	configFile.Close()
}

func executeHAproxyTemplate(services *common.Config, configFile *os.File, templateFile *template.Template) {
	var err = templateFile.Execute(configFile, services)
	if err != nil {
		log.Fatalf("Error applying services over template!", err)
	}
}


