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
	"fmt"
	"log"
	"net/http"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"sync"
	"time"
)

type ContainerShellMetadata struct {
	containerId  string
	socketPath   string
	shellProcess *os.Process
	LastAccessed time.Time
	lock         *sync.Mutex
}

type MonitorCache struct {
	containers map[string]*ContainerShellMetadata
	lock       *sync.Mutex
}

type handlerSocket func(w http.ResponseWriter, r *http.Request, socketPath string, urlPath string)
type handler func(w http.ResponseWriter, r *http.Request)

func getEnvFloat(key, defaultValue string) float64 {
	value := os.Getenv(key)
	if value == "" {
		value = defaultValue
	}

	intValue, _ := strconv.ParseFloat(value, 0)
	return intValue
}

// Variable to control the shell sessions check interval in minutes.
// Defaults to 1 minute
var SESSION_CHECK_INTERVAL = getEnvFloat("SESSION_CHECK_INTERVAL", "1")

// Variable to control the timeout in minutes after which a shell session is considered stalled.
// Defaults to 2 minutes
var SESSION_STALL_TIMEOUT = getEnvFloat("SESSION_STALL_TIMEOUT", "2")

const SHELL_ENDPOINT = "/shell/"

var cache = &MonitorCache{
	containers: make(map[string]*ContainerShellMetadata),
	lock:       &sync.Mutex{},
}

var selfContainerId string

func init() {
	var s, err = GetContainerId("admiral_agent")
	if err != nil {
		log.Printf("Could not get own container id %s\n", err)
		os.Exit(1)
	}
	selfContainerId = s

	startShellSessionChecker()
}

func StartOrUpdateShellProcess(hs handlerSocket) handler {
	return func(w http.ResponseWriter, r *http.Request) {
		var containerNameOrId = getContainerNameOrIdFromRequest(w, r)
		if containerNameOrId == "" {
			return
		}

		var containerId, err = GetContainerId(containerNameOrId)
		if err != nil {
			w.WriteHeader(http.StatusServiceUnavailable)
			log.Printf("GetContainerId error %s\n", err)
			return
		}
		if !validateContainerId(containerId, w) {
			return
		}

		cache.lock.Lock()
		var mc, exists = cache.containers[containerId]
		if !exists {
			var socketPath = getSocketPath(containerId)
			mc = &ContainerShellMetadata{
				containerId:  containerId,
				socketPath:   socketPath,
				shellProcess: nil,
				lock:         &sync.Mutex{},
			}
			cache.containers[containerId] = mc
		}
		cache.lock.Unlock()

		mc.lock.Lock()
		if mc.shellProcess == nil {
			if !validateContainerRunning(containerId, w) {
				return
			}
			var process, _ = startShell(containerId, mc.socketPath)
			mc.shellProcess = process
		}
		mc.LastAccessed = time.Now()
		mc.lock.Unlock()

		if mc.shellProcess == nil {
			w.WriteHeader(http.StatusServiceUnavailable)
			fmt.Fprintf(w, "There was a problem starting shell")
		} else {
			var containerPath = SHELL_ENDPOINT + containerNameOrId
			var urlPath = strings.Replace(r.URL.Path, containerPath, "", -1)
			hs(w, r, mc.socketPath, urlPath)
		}
	}
}

func getContainerNameOrIdFromRequest(w http.ResponseWriter, r *http.Request) string {
	var pathOnly = r.URL.Path[len(SHELL_ENDPOINT):]
	var split = strings.Split(pathOnly, "/")

	if len(split) > 1 {
		return split[0]
	} else {
		if split[0] == "favicon.ico" {
			// Do nothing yet
		} else if split[0] == "" {
			w.WriteHeader(http.StatusBadRequest)
			fmt.Fprintf(w, "Container name not provided")
		} else {
			// append trailing / to the path
			http.Redirect(w, r, r.URL.String()+"/", 302)
		}
		return ""
	}
}

func validateContainerId(containerId string, w http.ResponseWriter) bool {
	if containerId == selfContainerId {
		w.WriteHeader(http.StatusForbidden)
		log.Printf("Someone is trying to gain shell access to self container")
		return false
	}
	return true
}

func validateContainerRunning(containerId string, w http.ResponseWriter) bool {
	var b, err = IsContainerRunning(containerId)
	if err != nil {
		w.WriteHeader(http.StatusServiceUnavailable)
		log.Printf("validateContainerRunning error %s\n", err)
		return false
	}
	if !b {
		w.WriteHeader(http.StatusNotFound)
		fmt.Fprintf(w, "Container with id %s not running", containerId)
		return false
	}
	return true
}

func getSocketPath(containerId string) string {
	return fmt.Sprintf("/tmp/%s.sock", containerId)
}

func startShell(containerId string, socketPath string) (*os.Process, error) {
	log.Printf("Starting shell for (%s)\n", containerId)

	var cmd = exec.Command("shellinaboxd",
		"--localhost-only",
		"--disable-ssl",
		fmt.Sprintf("--unixdomain-only=%s:root:root:666", socketPath),
		"-f",
		"favicon.ico:/sib/terminal.png",
		"--css",
		"/sib/white-on-black.css",
		"--service",
		fmt.Sprintf("/:root:root:/:/agent/dockershell %s", containerId),
	)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	var err = cmd.Start()
	if err != nil {
		log.Printf("Problem starting shellinaboxd: %s\n", err)
		return nil, err
	} else {
		// Need some time for the server to start listen
		for !fileExists(socketPath) {
			time.Sleep(10 * time.Millisecond)
		}
		return cmd.Process, nil
	}
}

func stopShell(containerId string, containerShellMetadata *ContainerShellMetadata) {
	log.Printf("Stopping shell for (%s)\n", containerId)
	containerShellMetadata.shellProcess.Kill()
	os.Remove(containerShellMetadata.socketPath)
}

func fileExists(filePath string) bool {
	if _, err := os.Stat(filePath); os.IsNotExist(err) {
		return false
	}

	return true
}

func startShellSessionChecker() {
	var shellSessionsTicker = time.NewTicker(time.Duration(SESSION_CHECK_INTERVAL) * time.Minute)
	go func() {
		for {
			select {
			case <-shellSessionsTicker.C:
				shellSessionsChecker()
			}
		}
	}()
}

func shellSessionsChecker() {
	log.Printf("Checking shell sessions (%d)\n", len(cache.containers))
	for k, v := range cache.containers {
		v.lock.Lock()
		duration := time.Since(v.LastAccessed)
		if duration.Minutes() >= SESSION_STALL_TIMEOUT {
			stopShell(k, v)
			delete(cache.containers, k)
		}
		v.lock.Unlock()
	}
}
