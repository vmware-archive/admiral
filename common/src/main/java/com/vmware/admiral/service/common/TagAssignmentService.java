/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatelessService;

/**
 * Helper utility service for updating tags on a resource. Does all the heavy-lifting with regard
 * to self link retrieval, TagState creation, tag assignment/unassignment, and so on.
 *
 * If no resource is provided, the given tags are created if needed, and their links returned.
 *
 * Use a {@code POST} request with a {@code TagAssignmentRequest} body to create tag states in a
 * batch and retrieve their links.
 */
public class TagAssignmentService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.TAG_ASSIGNMENT;

    /**
     * Key-value structure for a tag.
     */
    public static class KeyValue {

        public KeyValue(String key, String value) {
            this.key = key;
            this.value = value;
        }

        String key;
        String value;
    }

    /**
     * Request/response body.
     */
    public static class TagAssignmentRequest extends MultiTenantDocument {
        //-----------------------------------------------------------------------------------------
        // Input fields

        /**
         * Optional resource which tags to update.
         */
        public String resourceLink;

        /**
         * Tags to unassign from the resource.
         */
        public List<KeyValue> tagsToUnassign;

        /**
         * Tags to assign on the resource. Corresponding TagState documents will be created, if
         * do not exist.
         */
        public List<KeyValue> tagsToAssign;

        /**
         * Whether the tags should be marked external or not. Pass {@code null} to make no change.
         */
        public Boolean external;

        //-----------------------------------------------------------------------------------------
        // Output fields

        /**
         * Populated by this service with the entire set of tag links on the resource after the
         * assignment/unassignment have been completed.
         */
        public Set<String> tagLinks;
    }

    public TagAssignmentService() {
        super(TagAssignmentRequest.class);
    }

    @Override
    public void handlePost(Operation post) {
        TagAssignmentRequest request = post.getBody(TagAssignmentRequest.class);

        postTagStates(request)
                .thenCompose(ignore -> updateTags(request))
                .thenAccept(tagLinks -> {
                    request.tagLinks = tagLinks;
                    post.setBody(request);
                })
                .whenCompleteNotify(post);
    }

    private DeferredResult<Void> postTagStates(TagAssignmentRequest request) {
        if (request.tagsToAssign == null || request.tagsToAssign.isEmpty()) {
            return DeferredResult.completed(null);
        }

        List<DeferredResult<Operation>> tagDRs = request.tagsToAssign.stream()
                .map(kv -> createTagState(request, kv))
                .map(this::createTagPostOperation)
                .map(op -> this.sendWithDeferredResult(op))
                .collect(Collectors.toList());

        return DeferredResult.allOf(tagDRs).thenApply(ops -> (Void) null);
    }

    private DeferredResult<Set<String>> updateTags(TagAssignmentRequest request) {
        if (request.resourceLink == null) {
            return DeferredResult.completed(getTagLinks(request, request.tagsToAssign));
        }

        Collection<Object> tagLinksToAdd =
                new HashSet<>(getTagLinks(request, request.tagsToAssign));
        Map<String, Collection<Object>> addMap = !tagLinksToAdd.isEmpty()
                ? Collections.singletonMap(ResourceState.FIELD_NAME_TAG_LINKS, tagLinksToAdd)
                : null;

        Collection<Object> tagLinksToRemove =
                new HashSet<>(getTagLinks(request, request.tagsToUnassign));
        Map<String, Collection<Object>> removeMap = !tagLinksToRemove.isEmpty()
                ? Collections.singletonMap(ResourceState.FIELD_NAME_TAG_LINKS, tagLinksToRemove)
                : null;

        ServiceStateCollectionUpdateRequest body =
                ServiceStateCollectionUpdateRequest.create(addMap, removeMap);
        Operation patchOp = Operation.createPatch(this.getHost(), request.resourceLink).setBody(body);
        return this.sendWithDeferredResult(patchOp)
                .thenCompose(op -> {
                    if (op.getStatusCode() == Operation.STATUS_CODE_NOT_MODIFIED) {
                        // in the case of 304, retrieve the resource state as it is not returned
                        return this.sendWithDeferredResult(
                                Operation.createGet(this.getHost(), request.resourceLink),
                                ResourceState.class);
                    } else {
                        return DeferredResult.completed(op.getBody(ResourceState.class));
                    }
                })
                .thenApply(rs -> rs.tagLinks);
    }

    private static TagState createTagState(TagAssignmentRequest request, KeyValue tag) {
        TagState tagState = new TagState();
        tagState.key = tag.key;
        tagState.value = tag.value;
        tagState.tenantLinks = request.tenantLinks;
        tagState.external = request.external;
        return tagState;
    }

    private Operation createTagPostOperation(TagState tagState) {
        return Operation.createPost(this.getHost(), TagService.FACTORY_LINK).setBody(tagState);
    }

    private Set<String> getTagLinks(TagAssignmentRequest request, List<KeyValue> tags) {
        if (tags == null) {
            return Collections.emptySet();
        }

        return tags.stream()
                .map(kv -> createTagState(request, kv))
                .map(TagFactoryService::generateSelfLink)
                .collect(Collectors.toSet());
    }
}
