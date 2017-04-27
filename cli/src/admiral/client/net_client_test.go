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

package client

import "testing"

func TestRemoveTrailingSlash(t *testing.T) {
	in := []string{"http://example.com/", "http://example.com", "http://你好.com/", "http://你好.com"}
	out := []string{"http://example.com", "http://example.com", "http://你好.com", "http://你好.com"}

	for i := range in {
		actual := urlRemoveTrailingSlash(in[i])
		if actual != out[i] {
			t.Errorf("Expected: %s, got: %s", out[i], actual)
		}
	}
}
