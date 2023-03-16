package common

type Inbound struct {
	Id   int64  `json:"id"`
	Data string `json:"data"`
}

type Outbound struct {
	Id     int64  `json:"id"`
	Data   string `json:"data"`
	Elapse int64  `json:"elapse"`
}
