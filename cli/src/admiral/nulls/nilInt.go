package nulls

import (
	"encoding/json"
)

type NilInt64 struct {
	Value int64
}

func (n NilInt64) MarshalJSON() ([]byte, error) {
	if n.Value == 0 {
		return json.Marshal(nil)
	}
	return json.Marshal(n.Value)
}

type NilInt32 struct {
	Value int32
}

func (n NilInt32) MarshalJSON() ([]byte, error) {
	if n.Value == 0 {
		return json.Marshal(nil)
	}
	return json.Marshal(n.Value)
}
