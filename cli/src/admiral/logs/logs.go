package logs

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"

	"admiral/client"
	"admiral/config"
	"admiral/functions"
)

type LogResponse struct {
	Logs string `json:"logs"`
}

func GetLog(contName, since string) {
	id := "id=" + contName
	sinceQ := "&since=" + since

	url := config.URL + "/resources/container-logs?" + id + sinceQ
	lresp := &LogResponse{}

	req, _ := http.NewRequest("GET", url, nil)
	resp, respBody := client.ProcessRequest(req)
	defer resp.Body.Close()
	err := json.Unmarshal(respBody, lresp)
	functions.CheckJson(err)
	log, err := base64.StdEncoding.DecodeString(lresp.Logs)
	if err != nil {
		fmt.Println(err.Error())
	}
	fmt.Println(string(log))
}
