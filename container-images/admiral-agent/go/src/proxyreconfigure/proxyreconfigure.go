package main

import (
    "text/template"
    "os"
    "os/exec"
    "strings"
    "flag"
    "fmt"
    "log"
    "strconv"
    "cmp/common"
)

type ProxyConfig struct {
    Frontends map[string]*Frontend
    PublicFrontends map[string]*PublicFrontend
    Backends map[string]*Backend
    InternalIP string
    PublicIP string
}

type Frontend struct {
    Name string
    Port int64
}

type PublicFrontend struct {
    Scheme string
    Alias string
}

type Backend struct {
    Name string
    Nodes map[string]*BackendNode
}

type BackendNode struct {
    Id string
    Address string
}

type arrayFlags []string

func (i *arrayFlags) String() string {
    return "arrayFlags.String()"
}

func (i *arrayFlags) Set(value string) error {
    *i = append(*i, value)
    return nil
}

func main() {
    var logErr = common.StartLoggingToFile("/var/log/proxyreconfigure.log", "proxyreconfigure")
    if logErr != nil {
        log.Fatal(logErr)
        os.Exit(1)
    }
    log.Printf("===========================")
    log.Printf("Start reconfiguring process")

    var internalConfigArrayFlags,
        publicConfigArrayFlags = extractArguments()

    internalIPBytes, iperr := exec.Command("/docker-ip.sh").Output()
    if iperr != nil {
        log.Fatal(iperr)
        os.Exit(1)
    }

    internalIP := strings.TrimSpace(string(internalIPBytes))
    publicIP := "*"

    var err = updateConfiguration(internalConfigArrayFlags, publicConfigArrayFlags, internalIP, publicIP)

    if err != nil {
        log.Fatal(err)
        os.Exit(1)
    }

    var reloadErr = common.ReloadHAproxy()
    if reloadErr != nil {
        log.Printf("Complete reconfiguring process with error")
        log.Printf("===========================")
        os.Exit(1)
    } else {
        log.Printf("Complete reconfiguring process")
        log.Printf("===========================")
    }
}

func generateInternalFrontendName(suffix int64) string {
    return fmt.Sprintf("internal_%d", suffix);
}

func generateBackendName(containerName string, servicePort int64) string {
    return fmt.Sprintf("%s_%d", containerName, servicePort);
}

func extractArguments() (*arrayFlags, *arrayFlags) {
    var internalConfigArrayFlags arrayFlags
    flag.Var(&internalConfigArrayFlags, "i", "Internal container to service configuration in the form of src-container-name:dest-service-port:dest-host-ip:dest-host-port. \nExample: -i happy_knuth:80:10.0.0.1:32782")

    var publicConfigArrayFlags arrayFlags
    flag.Var(&publicConfigArrayFlags, "p", "External public to service configuration by hostname in the form of hostname:dest-host-ip:dest-host-port. \nExample: -p web.foo.com:10.0.0.1:32782")

    flag.Parse()

    return &internalConfigArrayFlags, &publicConfigArrayFlags
}

func parseProxyConfigArguments(internalConfigArrayFlags *arrayFlags,
    publicConfigArrayFlags *arrayFlags, internalIP string, publicIP string) (*ProxyConfig, error) {
    var frontendMap map[string]*Frontend = make(map[string]*Frontend)
    var publicFrontendMap map[string]*PublicFrontend = make(map[string]*PublicFrontend)
    var backendMap map[string]*Backend = make(map[string]*Backend)

    for _, internalConfig := range *internalConfigArrayFlags {
        var split = strings.Split(internalConfig, ":")
        if len(split) != 4 {
            return nil, fmt.Errorf("Unrecognized internal pattern %s", internalConfig)
        }

        var containerName = split[0]
        var servicePort, _ = strconv.ParseInt(split[1], 10, 32)
        var hostIp = split[2]
        var hostPort, _ = strconv.ParseInt(split[3], 10, 32)

        var frontendName = generateInternalFrontendName(servicePort)
        var _, fexists = frontendMap[frontendName]
        if !fexists {
            var frontend = &Frontend{
                Name: frontendName,
                Port: servicePort,
            }
            frontendMap[frontendName] = frontend
        }

        var backendName = generateBackendName(containerName, servicePort)
        var backend, bexists = backendMap[backendName]
        if !bexists {
            backend = &Backend{
                Name: backendName,
                Nodes: make(map[string]*BackendNode),
            }
            backendMap[backendName] = backend
        }

        var address = fmt.Sprintf("%s:%d", hostIp, hostPort)
        var _, nexists = backend.Nodes[address]
        if !nexists {
            var id = fmt.Sprintf("node-%d", len(backend.Nodes) + 1)

            var node = &BackendNode{
                Address: address,
                Id: id,
            }
            backend.Nodes[address] = node
        }
    }

    for _, publicConfig := range *publicConfigArrayFlags {
        var split = strings.Split(publicConfig, ":")
        if len(split) != 4 {
            return nil, fmt.Errorf("Unrecognized public service address pattern %s", publicConfig)
        }

        var scheme = split[0]
        var alias = split[1]
        var hostIp = split[2]
        var hostPort, _ = strconv.ParseInt(split[3], 10, 32)

        var configId = fmt.Sprintf("public_%s", alias)
        var backendHostId = fmt.Sprintf("host_%s", alias)
        var publicFrontend = &PublicFrontend{
            Scheme: scheme,
            Alias: alias,
        }

        publicFrontendMap[configId] = publicFrontend

        var address = fmt.Sprintf("%s:%d", hostIp, hostPort)
        addPublicBackend(backendHostId, address, backendMap)
    }

    var proxyConfig = &ProxyConfig{
        Frontends: frontendMap,
        PublicFrontends: publicFrontendMap,
        Backends: backendMap,
        InternalIP: internalIP,
        PublicIP: publicIP,
    }

    return proxyConfig, nil
}

func addPublicBackend(id string, address string, backendMap map[string]*Backend) {
    var backend, bexists = backendMap[id]
    if !bexists {
        backend = &Backend{
            Name: id,
            Nodes: make(map[string]*BackendNode),
        }

        backendMap[id] = backend
    }
    var _, nexists = backend.Nodes[address]
    if !nexists {
        var id = fmt.Sprintf("node-%d", len(backend.Nodes) + 1)

        var node = &BackendNode{
            Address: address,
            Id: id,
        }
        backend.Nodes[address] = node
    }
}

func updateConfiguration(internalConfigArrayFlags *arrayFlags,
    publicConfigArrayFlags *arrayFlags, internalIP string, publicIP string) error {
    proxyConfig, err := parseProxyConfigArguments(internalConfigArrayFlags, publicConfigArrayFlags, internalIP, publicIP)

    if err != nil {
        return err
    }

    file, err := os.Create(common.GetHAproxyPath(common.HAPROXY_CONFIG_FILE))
    if err != nil {
        return err
    }

    output := &common.Striplines {
        Writer: file,
    }

    haproxyTemplate, err := template.ParseFiles(common.GetHAproxyPath(common.HAPROXY_CONFIG_TEMPLATE_FILE))
    if err != nil {
        return err
    }

    err = haproxyTemplate.Execute(output, proxyConfig)
    if err != nil {
        return err
    }

    return nil
}