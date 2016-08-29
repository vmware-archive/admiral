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