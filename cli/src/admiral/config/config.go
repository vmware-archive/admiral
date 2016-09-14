package config

import (
	"encoding/json"
	"fmt"
	"os"
	"reflect"
	"strconv"
	"strings"

	"admiral/functions"
	"admiral/paths"
)

type Config struct {
	Url     string `json:"url"`
	User    string `json:"user"`
	Timeout string `json:"timeout"`
}

//Default Values
var (
	defaultURL     = "http://127.0.0.1:8282"
	defaultUSER    = ""
	defaultTimeout = 70
)

//Values used from commands.
var (
	URL     string
	USER    string
	TIMEOUT int
)

//GetCfg is trying to load configurable properties from the config file.
//If file is missing, will load default properties and create file with them.
func GetCfg() {
	file, err := os.Open(paths.ConfigPath())
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

	if strings.TrimSpace(cfg.Timeout) == "" {
		TIMEOUT = defaultTimeout
	} else {
		TIMEOUT, err = strconv.Atoi(cfg.Timeout)
		functions.CheckParse(err)
	}
}

//createDefaultCfgFile is creating file with default values of the
//configurable properties.
func createDefaultCfgFile() {
	cfg := &Config{
		Url:     defaultURL,
		User:    defaultUSER,
		Timeout: strconv.Itoa(defaultTimeout),
	}
	paths.MkCliDir()
	file, err := os.Create(paths.ConfigPath())
	defer file.Close()
	functions.CheckFile(err)
	jsonCfg, err := json.MarshalIndent(cfg, "", "    ")
	file.Write(jsonCfg)
}

//GetProperty is used to get property by key from the config file.
func GetProperty(key string) reflect.Value {
	file, _ := os.Open(paths.ConfigPath())
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
	file, _ := os.Open(paths.ConfigPath())
	defer file.Close()
	cfg := &Config{}
	decoder := json.NewDecoder(file)
	_ = decoder.Decode(cfg)
	v := reflect.ValueOf(cfg).Elem()
	v.FieldByName(key).SetString(val)
	jsonCfg, _ := json.MarshalIndent(cfg, "", "    ")
	file.Close()
	paths.MkCliDir()
	file, _ = os.Create(paths.ConfigPath())
	_, err := file.Write(jsonCfg)
	if err != nil {
		fmt.Println(err.Error())
		return false
	}
	return true
}

//Inspect returns the content of the config file in json format as byte array.
func Inspect() []byte {
	file, err := os.Open(paths.ConfigPath())
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
	functions.CheckJson(err)
	return jsonBody
}
