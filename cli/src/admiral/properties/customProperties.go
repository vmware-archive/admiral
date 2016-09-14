package properties

import "regexp"

func ParseCustomProperties(props []string) map[string]*string {
	if len(props) < 1 {
		return nil
	}
	regex := regexp.MustCompile("\\w+\\s*=\\s*\\w+")

	properties := make(map[string]*string)

	for _, pair := range props {
		if !regex.MatchString(pair) {
			continue
		}
		keyVal := regexp.MustCompile("\\s*=\\s*").Split(pair, -1)
		properties[keyVal[0]] = &keyVal[1]
	}
	return properties
}

func AddCredentialsName(name string, props map[string]*string) map[string]*string {
	props["__authCredentialsName"] = &name
	return props
}

func MakeHostProperties(cred, dp string, props map[string]*string) map[string]*string {
	props["__authCredentialsLink"] = &cred
	props["__deploymentPolicyLink"] = &dp
	api := "API"
	props["__adapterDockerType"] = &api
	return props
}
