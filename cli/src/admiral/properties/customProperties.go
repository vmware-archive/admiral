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

func ParseCustomProperties(props []string, dst map[string]*string) {
	if dst == nil {
		dst = make(map[string]*string, 0)
	}
	if len(props) < 1 {
		return
	}
	regex := regexp.MustCompile("\\w+\\s*=\\s*\\w+")

	for _, pair := range props {
		if !regex.MatchString(pair) {
			continue
		}
		keyVal := regexp.MustCompile("\\s*=\\s*").Split(pair, -1)
		dst[keyVal[0]] = &keyVal[1]
	}

}

func AddCredentialsName(name string, props map[string]*string) {
	props["__authCredentialsName"] = &name
}

func MakeHostProperties(credLink, dpLink, name string, dst map[string]*string) {
	if dst == nil {
		dst = make(map[string]*string, 0)
	}
	if credLink != "" {
		dst["__authCredentialsLink"] = &credLink
	}
	if dpLink != "" {
		dst["__deploymentPolicyLink"] = &dpLink
	}
	if name != "" {
		dst["__hostAlias"] = &name
	}
	api := "API"
	dst["__adapterDockerType"] = &api

}
