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
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

public interface PrincipalProvider {

    void init(Service service);

    DeferredResult<Principal> getPrincipal(Operation op, String principalId);

    DeferredResult<List<Principal>> getPrincipals(Operation op, String criteria);

    DeferredResult<Principal> createPrincipal(Operation op, Principal principal);

    DeferredResult<Principal> updatePrincipal(Operation op, Principal principal);

    DeferredResult<Principal> deletePrincipal(Operation op, String principalId);

    DeferredResult<Set<String>> getAllGroupsForPrincipal(Operation op, String principalId);
}
