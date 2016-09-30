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
    "log"
    "fmt"
    "os"
    "net/http"
)

func main() {
    http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
        log.Printf("Got request from %s\n", r.RemoteAddr)
        fmt.Fprintf(w, "Hello from %s", os.Getenv("HOSTNAME"))
    })

    log.Fatal(http.ListenAndServe("0.0.0.0:80", nil))
}