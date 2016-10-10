/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

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

func MakeHostProperties(credLink, dpLink string, props map[string]*string) map[string]*string {
	if credLink != "" {
		props["__authCredentialsLink"] = &credLink
	}
	if dpLink != "" {
		props["__deploymentPolicyLink"] = &dpLink
	}
	api := "API"
	props["__adapterDockerType"] = &api
	return props
}
