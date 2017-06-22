/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.auth.idm;

import java.util.List;
import java.util.Set;

import com.vmware.xenon.common.DeferredResult;

public interface PrincipalProvider {

    DeferredResult<Principal> getPrincipal(String principalId);

    DeferredResult<List<Principal>> getPrincipals(String criteria);

    DeferredResult<Principal> createPrincipal(Principal principal);

    DeferredResult<Principal> updatePrincipal(Principal principal);

    DeferredResult<Principal> deletePrincipal(String principalId);

    DeferredResult<Set<String>> getAllGroupsForPrincipal(String principalId);
}
