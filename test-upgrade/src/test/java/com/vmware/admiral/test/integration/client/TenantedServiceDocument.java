/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration.client;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Base class for all {@link ServiceDocument}s that have <code>group</group> property and are tenant
 * aware.
 */
@XmlTransient
public abstract class TenantedServiceDocument extends ServiceDocument {
    public static final String FIELD_NAME_TENANT_LINKS = "tenantLinks";
    public static final String TENANT_PREFIX = "/tenants/";
    public static final String GROUP_PREFIX = "/groups/";
    public static final String USER_PREFIX = "/users/";

    /** Tenant and Business Group. */
    @XmlAttribute
    private List<String> tenantLinks;

    // tenant = /tenants/coke
    // subTenant = /tenants/coke/groups/dev
    public static String getTenantLinks(String tenant, String subTenant) {
        if (tenant == null) {
            return null;
        }
        List<String> tenantLinks = formatTenantAndGroup(tenant, subTenant);

        if (tenantLinks.size() == 1) {
            return tenantLinks.get(0);
        }

        return tenantLinks.get(1);
    }

    public static String getUserLink(List<String> tenantLinks) {
        String userLink = getMatchingElement(tenantLinks, TenantedServiceDocument.USER_PREFIX);

        return userLink;
    }

    public static TenantScopeInfo getTenantAndSubTenant(String tenantLinks) {

        if (tenantLinks != null && !tenantLinks.isEmpty()) {
            TenantScopeInfo tenantInfo = new TenantScopeInfo();
            int indexOfTenant = tenantLinks.lastIndexOf(TENANT_PREFIX);
            int indexOfGroup = tenantLinks.lastIndexOf(GROUP_PREFIX);
            if (tenantLinks.contains(TENANT_PREFIX)) {
                if (indexOfGroup != -1) {
                    tenantInfo.tenant = tenantLinks.substring(
                            indexOfTenant + TENANT_PREFIX.length(), indexOfGroup);
                } else {
                    tenantInfo.tenant = tenantLinks.substring(indexOfTenant
                            + TENANT_PREFIX.length());
                }
            }

            if (tenantLinks.contains(GROUP_PREFIX)) {
                String group = tenantLinks.substring(indexOfGroup + GROUP_PREFIX.length());
                // Remove '/' character at the end of the group if it is provided.
                if (group.endsWith("/")) {
                    group = group.substring(0, group.length() - 1);
                }
                tenantInfo.subTenant = group;
            }

            return tenantInfo;
        }

        return getTenantAndSubTenant(Collections.<String> emptyList());
    }

    public static TenantScopeInfo getTenantAndSubTenant(List<String> tenantLinks) {
        TenantScopeInfo tenantInfo = new TenantScopeInfo();
        if (tenantLinks == null || tenantLinks.isEmpty()) {
            return tenantInfo;
        } else if (tenantLinks.size() == 1) {
            String tenant = findTenantOrGroup(tenantLinks, true);
            tenantInfo.tenant = tenant;

            String group = findTenantOrGroup(tenantLinks, false);
            if (group != null) {
                tenantInfo.subTenant = group;
            }

            return tenantInfo;
        } else {
            String tenant = findTenantOrGroup(tenantLinks, true);
            String subtenant = findTenantOrGroup(tenantLinks, false);
            tenantInfo.tenant = tenant;
            tenantInfo.subTenant = subtenant;
            return tenantInfo;
        }
    }

    public String getTenant() {
        return getTenantAndSubTenant(getTenantLinks()).tenant;
    }

    public void setTenant(String tenant) {

        setTenantLinks(Collections.singletonList(getTenantLinks(tenant,
                getSubTenant())));
    }

    public String getSubTenant() {
        return getTenantAndSubTenant(getTenantLinks()).subTenant;
    }

    public void setSubTenant(String subTenant) {
        setTenantAndSubTenant(getTenant(), subTenant);
    }

    public void setTenantAndSubTenant(String tenant, String subTenant) {
        setTenantLinks(formatTenantAndGroup(tenant, subTenant));
    }

    public boolean isSameTenant(String tenant) {
        String currentTenant = getTenant();
        if (currentTenant == null) {
            return true;
        } else if (tenant == null) {
            return false;
        } else {
            return tenant.equalsIgnoreCase(currentTenant);
        }
    }

    public boolean isSameSubTenant(String subTenant) {
        String currentSubTenant = getSubTenant();
        if (currentSubTenant == null) {
            return true;
        } else if (subTenant == null) {
            return false;
        } else {
            return subTenant.equalsIgnoreCase(currentSubTenant);
        }
    }

    public boolean isSameUser(String user) {
        if (tenantLinks == null) {
            return true;
        }

        return tenantLinks.contains(USER_PREFIX + user);

    }

    public List<String> getTenantLinks() {
        return tenantLinks;
    }

    public void setTenantLinks(List<String> tenantLinks) {
        this.tenantLinks = tenantLinks;
    }

    public void addToTenantLinks(String value) {
        if (tenantLinks == null) {
            throw new IllegalArgumentException("Tenant links cannot be null!");
        }

        List<String> tenantLinks = new LinkedList<>(this.tenantLinks);
        tenantLinks.add(value);

        setTenantLinks(tenantLinks);
    }

    public static class TenantScopeInfo {
        public String tenant;
        public String subTenant;
    }

    /**
     * Formats tenant and business group in format: tenant/{tenantName}/group/{groupName}
     *
     * @param tenant
     * @param group
     * @return
     */
    public static List<String> formatTenantAndGroup(String tenant, String group) {
        List<String> tenantAndGroup = new LinkedList<String>();
        // First check if both tenant & group are not empty.
        if ((tenant != null && group != null)
                && (!tenant.isEmpty() && !group.isEmpty())) {
            String formattedTenant = String.format("%s%s", TENANT_PREFIX, tenant);
            String formattedGroup = String.format("%s%s%s%s", TENANT_PREFIX,
                    tenant, GROUP_PREFIX, group);
            tenantAndGroup.add(formattedTenant);
            tenantAndGroup.add(formattedGroup);
        } else if (tenant != null && !tenant.isEmpty()) {
            // If statement is true, this means that group is not provided.Only tenant will be
            // returned.
            String formattedTenant = String.format("%s%s", TENANT_PREFIX, tenant);
            tenantAndGroup.add(formattedTenant);
        } else {
            throw new IllegalArgumentException(
                    "Tenant could not be 'null' or 'empty'!");
        }
        return tenantAndGroup;
    }

    /**
     * Finds tenant or business group.
     *
     * @param tenantLinks
     *            - List contains tenant and group in format: ['/tenant/tenantId',
     *            '/tenants/tenantId/groups/groupId']
     * @param tenant
     *            - boolean flag which indicates whether tenant or business group will be returned.
     * @return escaped tenant or group. For example: for element '/tenants/tenantId', only
     *         'tenantId' will be returned. Same for group.
     */
    public static String findTenantOrGroup(List<String> tenantLinks, boolean tenant) {
        String result = null;

        for (String value : tenantLinks) {
            if (tenant && value.contains(TENANT_PREFIX)
                    && !value.contains(GROUP_PREFIX)) {
                if (result != null) {
                    throw new IllegalArgumentException(
                            "Only one tenant in format '/tenant/tenantId' could exists.");
                }
                int indexOfTenant = value.lastIndexOf(TENANT_PREFIX);
                result = value
                        .substring(indexOfTenant + TENANT_PREFIX.length());
            } else if (!tenant && value.contains(GROUP_PREFIX)) {
                int indexOfGroup = value.lastIndexOf(GROUP_PREFIX);
                result = value.substring(indexOfGroup + GROUP_PREFIX.length());
                if (result.endsWith("/")) {
                    result = result.substring(0, result.length() - 1);
                }
                // // check if tenant for the the group exists in tenant links.
                // if (!tenantLinks.contains(value.substring(0, indexOfGroup)))
                // {
                // throw new IllegalArgumentException(
                // "Invalid group format.Proper values must be in following format: tenant - >
                // tenants/{tenantName}; group -> tenants/{tenantName}/groups/{groupName}.");
                // }
            }
        }

        // In the list there is only one value in format /tenants/tenantId/groups/groupId.
        if (result == null && tenant) {
            result = getTenantAndSubTenant(tenantLinks.get(0)).tenant;
        }

        return result;
    }

    /**
     * Formats the tenant and group and append the user
     *
     * @param tenant
     * @param group
     * @param user
     * @return
     */
    public static List<String> prepareTenantLinks(String tenant, String group, String user) {
        List<String> tenantAndGroup = formatTenantAndGroup(tenant, group);

        if (user != null) {
            tenantAndGroup.add(String.format("%s%s", USER_PREFIX, user));
        }

        return tenantAndGroup;
    }

    private static String getMatchingElement(List<String> list, String matchValue) {
        if (list != null) {
            for (String element : list) {
                if (element.contains(matchValue)) {
                    return element;
                }
            }
        }

        return null;
    }

}
