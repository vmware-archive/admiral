package nulls

import (
	"encoding/json"
)

type NilString struct {
	Value string
}

func (n NilString) MarshalJSON() ([]byte, error) {
	if n.Value == "" {
		return json.Marshal(nil)
	}
	return json.Marshal(n.Value)
}
