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

package com.vmware.admiral.adapter.etcd.service;

import static com.vmware.admiral.common.util.ServiceUtils.addServiceRequestRoute;

import java.util.ArrayList;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.NodeGroupService.NodeGroupState;
import com.vmware.xenon.services.common.NodeState;
import com.vmware.xenon.services.common.NodeState.NodeOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Stateless services that emulates the etcd members API.
 * See https://coreos.com/etcd/docs/latest/members_api.html
 */
public class EtcdMembersService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_ETCD_MEMBERS;

    // TODO: remove when API is stable
    private static final String DEV_MODE_CLIENT_URL = System
            .getProperty("dev.mode.etcd.client.url");

    public static class EtcdMembers {
        public ArrayList<EtcdMember> members;
    }

    public static class EtcdMember {
        String id;
        String name;
        ArrayList<String> peerURLs;
        ArrayList<String> clientURLs;
    }

    public EtcdMembersService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleGet(Operation get) {
        sendRequest(Operation.createGet(this, ServiceUriPaths.DEFAULT_NODE_GROUP)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        get.fail(e);
                    } else {

                    }
                    get.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
                    NodeGroupState nodeGroup = o.getBody(NodeGroupState.class);
                    get.setBodyNoCloning(toEtcdMembers(nodeGroup,
                            ServiceUriPaths.DEFAULT_NODE_GROUP));
                    get.complete();
                }));
    }

    public static EtcdMembers toEtcdMembers(NodeGroupState in, String groupPath) {
        if (in == null) {
            return null;
        }
        EtcdMembers out = new EtcdMembers();
        out.members = new ArrayList<>();
        ArrayList<String> peerURLs = new ArrayList<>();

        for (NodeState nodeState : in.nodes.values()) {
            if (nodeState.options.contains(NodeOption.PEER)) {

                String url = getNodeURL(nodeState, groupPath);
                peerURLs.add(url);
            }
        }

        for (NodeState nodeState : in.nodes.values()) {
            if (nodeState.options.contains(NodeOption.PEER)) {
                out.members.add(toEtcdMember(nodeState, groupPath, peerURLs));
            }
        }

        return out;
    }

    public static EtcdMember toEtcdMember(NodeState in, String groupPath, ArrayList<String> peerURLs) {
        if (in == null) {
            return null;
        }

        EtcdMember out = new EtcdMember();
        out.id = in.id;
        out.name = in.id;
        out.peerURLs = peerURLs;
        out.clientURLs = new ArrayList<>();
        out.clientURLs.add(getNodeURL(in, groupPath));

        return out;
    }

    private static String getNodeURL(NodeState node, String groupPath) {
        if (DEV_MODE_CLIENT_URL != null && !DEV_MODE_CLIENT_URL.isEmpty()) {
            return DEV_MODE_CLIENT_URL;
        }

        return node.groupReference.toString().replace(groupPath, "");
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        addServiceRequestRoute(d, Action.GET,
                "Get etcd members.", EtcdMembers.class);
        return d;
    }
}