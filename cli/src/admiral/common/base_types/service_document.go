package base_types

type ServiceDocument struct {
	DocumentVersion           int64       `json:"documentVersion,omitempty"`
	DocumentEpoch             int64       `json:"documentEpoch,omitempty"`
	DocumentKind              string      `json:"documentKind,omitempty"`
	DocumentSelfLink          string      `json:"documentSelfLink,omitempty"`
	DocumentUpdateTimeMicros  interface{} `json:"documentUpdateTimeMicros,omitempty"`
	DocumentUpdateAction      string      `json:"documentUpdateAction,omitempty"`
	DocumentExpirationTime    interface{} `json:"documentExpirationTime,omitempty"`
	DocumentOwner             string      `json:"documentOwner,omitempty"`
	DocumentSourceLink        string      `json:"documentSourceLink,omitempty"`
	DocumentAuthPrincipalLink string      `json:"documentAuthPrincipalLink,omitempty"`
	DocumentTransactionId     string      `json:"documentTransactionId,omitempty"`
}
