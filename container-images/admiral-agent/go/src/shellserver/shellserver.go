package main

import (
    "log"
    "net/http"
    "os"
    "cmp/common"
)

func main() {
    var logErr = common.StartLoggingToFile("/var/log/shellserver.log", "shellserver")
    if logErr != nil {
        log.Fatal(logErr)
        os.Exit(1)
    }

    http.HandleFunc(SHELL_ENDPOINT, StartOrUpdateShellProcess(ProxyToSocket))

    log.Fatal(http.ListenAndServe("0.0.0.0:4200", nil))
}
