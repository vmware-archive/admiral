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

package config

import (
	"encoding/json"
	"fmt"
	"os"
	"reflect"
	"strconv"
	"strings"

	"admiral/utils"
)

type Config struct {
	Url           string `json:"url"`
	User          string `json:"user"`
	TaskTimeout   string `json:"taskTimeout"`
	ClientTimeout string `json:"clientTimeout"`
}

//Default Values
var (
	defaultURL           = "http://127.0.0.1:8282"
	defaultUSER          = ""
	defaultTaskTimeout   = 70
	defaultClientTimeout = 70
)

//Values used from commands.
var (
	URL            string
	USER           string
	TASK_TIMEOUT   int
	CLIENT_TIMEOUT int
)

//GetCfg is trying to load configurable properties from the config file.
//If file is missing, will load default properties and create file with them.
func GetCfg() {
	file, err := os.Open(utils.ConfigPath())
	defer file.Close()
	if err != nil {
		createDefaultCfgFile()
	}
	cfg := &Config{}
	decoder := json.NewDecoder(file)
	err = decoder.Decode(cfg)
	if err != nil {
		createDefaultCfgFile()
	}

	if strings.TrimSpace(cfg.Url) == "" {
		URL = defaultURL
	} else {
		URL = cfg.Url
	}

	if strings.TrimSpace(cfg.User) == "" {
		USER = defaultUSER
	} else {
		USER = cfg.User
	}

	if strings.TrimSpace(cfg.TaskTimeout) == "" {
		TASK_TIMEOUT = defaultTaskTimeout
	} else {
		TASK_TIMEOUT, err = strconv.Atoi(cfg.TaskTimeout)
		utils.CheckParse(err)
	}

	if strings.TrimSpace(cfg.ClientTimeout) == "" {
		CLIENT_TIMEOUT = defaultClientTimeout
	} else {
		CLIENT_TIMEOUT, err = strconv.Atoi(cfg.ClientTimeout)
		utils.CheckParse(err)
	}
}

//createDefaultCfgFile is creating file with default values of the
//configurable properties.
func createDefaultCfgFile() {
	cfg := &Config{
		Url:           defaultURL,
		User:          defaultUSER,
		TaskTimeout:   strconv.Itoa(defaultTaskTimeout),
		ClientTimeout: strconv.Itoa(defaultClientTimeout),
	}
	utils.MkCliDir()
	file, err := os.Create(utils.ConfigPath())
	defer file.Close()
	utils.CheckFile(err)
	jsonCfg, err := json.MarshalIndent(cfg, "", "    ")
	file.Write(jsonCfg)
}

//GetProperty is used to get property by key from the config file.
func GetProperty(key string) reflect.Value {
	file, _ := os.Open(utils.ConfigPath())
	defer file.Close()
	cfg := &Config{}
	decoder := json.NewDecoder(file)
	_ = decoder.Decode(cfg)
	v := reflect.ValueOf(cfg).Elem()
	return v.FieldByName(key)
}

//SetProperty is used to set property by given key and value
//that will be assigned to this key.
func SetProperty(key, val string) bool {
	file, _ := os.Open(utils.ConfigPath())
	defer file.Close()
	cfg := &Config{}
	decoder := json.NewDecoder(file)
	_ = decoder.Decode(cfg)
	v := reflect.ValueOf(cfg).Elem()
	if !v.FieldByName(key).IsValid() {
		return false
	}
	v.FieldByName(key).SetString(val)
	jsonCfg, _ := json.MarshalIndent(cfg, "", "    ")
	file.Close()
	utils.MkCliDir()
	file, _ = os.Create(utils.ConfigPath())
	_, err := file.Write(jsonCfg)
	if err != nil {
		fmt.Println(err.Error())
		return false
	}
	return true
}

//Inspect returns the content of the config file in json format as byte array.
func Inspect() []byte {
	file, err := os.Open(utils.ConfigPath())
	defer file.Close()
	if err != nil {
		createDefaultCfgFile()
	}
	cfg := &Config{}
	decoder := json.NewDecoder(file)
	err = decoder.Decode(cfg)
	if err != nil {
		createDefaultCfgFile()
	}
	jsonBody, err := json.MarshalIndent(cfg, "", "    ")
	utils.CheckJson(err)
	return jsonBody
}
