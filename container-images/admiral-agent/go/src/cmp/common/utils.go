package common

import (
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"os"
	"os/exec"
	"time"
)

const HAPROXY_CONFIG_FILE = "haproxy.cfg"
const HAPROXY_CONFIG_TEMPLATE_FILE = "haproxy.cfg.template"
const HAPROXY_RELOAD_SCRIPT = "haproxy-reload.sh"

type LogWriter struct {
}

func (lw LogWriter) Write(b []byte) (n int, err error) {
	log.Printf("%s", b)
	return len(b), nil
}

func ReloadHAproxy() error {
	log.Printf("Hot reloading HAproxy")

	var cmd = exec.Command(GetHAproxyPath(HAPROXY_RELOAD_SCRIPT))
	var lw = &LogWriter{}

	cmd.Stdout = lw
	cmd.Stderr = lw
	var err = cmd.Run()
	if err != nil {
		var cfgBytes, _ = ioutil.ReadFile(GetHAproxyPath(HAPROXY_CONFIG_FILE))
		if cfgBytes != nil {
			log.Printf("Configuration: \n%s ", string(cfgBytes))
		} else {
			log.Printf("Could not read configuration")
		}
		log.Printf("Hot reloading failed")
		return err
	} else {
		log.Printf("Hot reloading success")
		return nil
	}
}

func GetHAproxyPath(relPath string) string {
	return fmt.Sprintf("/haproxy/%s", relPath)
}

type CustomWriter struct {
	TargetWriter io.Writer
	prefix       string
}

func (writer *CustomWriter) Write(bytes []byte) (int, error) {
	return fmt.Fprintf(writer.TargetWriter, "%s %s: %s", time.Now().Format("2006-01-02T15:04:05-07:00"), writer.prefix, string(bytes))
}

func StartLoggingToFile(filename string, prefix string) error {
	var f, errLog = os.OpenFile(filename, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0600)
	if errLog != nil {
		return errLog
	}

	output := &CustomWriter{
		TargetWriter: f,
		prefix:       prefix,
	}

	log.SetFlags(0)
	log.SetOutput(output)
	return nil
}
