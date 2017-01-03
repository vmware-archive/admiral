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
	"cmp/common"
	"log"
	"net/http"
	"os"
)

func main() {
	var logErr = common.StartLoggingToFile("/var/log/shellserver.log", "shellserver")
	if logErr != nil {
		log.Fatal(logErr)
		os.Exit(1)
	}

	http.HandleFunc(SHELL_ENDPOINT, StartOrUpdateShellProcess(ProxyToSocket))

	log.Fatal(http.ListenAndServeTLS("0.0.0.0:4200", "/agent/server.crt", "/agent/server.key", nil))
}
