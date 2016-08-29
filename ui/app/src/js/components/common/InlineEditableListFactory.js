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

import InlineEditableList from 'components/common/InlineEditableList';
//resource pools
import ResourcePoolsTemplate from 'ResourcePoolsTemplate';
import ResourcePoolsRowRenderers from 'components/resourcepools/ResourcePoolsRowRenderers';
import ResourcePoolsRowEditor from 'components/resourcepools/ResourcePoolsRowEditor';
// credentials
import CredentialsTemplate from 'CredentialsTemplate';
import CredentialsRowRenderers from 'components/credentials/CredentialsRowRenderers';
import CredentialsRowEditor from 'components/credentials/CredentialsRowEditor';
// certificates
import CertificatesTemplate from 'CertificatesTemplate';
import CertificatesRowRenderers from 'components/certificates/CertificatesRowRenderers';
import CertificatesRowEditor from 'components/certificates/CertificatesRowEditor';
// resource groups
import ResourceGroupsTemplate from 'ResourceGroupsTemplate';
import ResourceGroupsRowRenderers from 'components/resourcegroups/ResourceGroupsRowRenderers';
import ResourceGroupsRowEditor from 'components/resourcegroups/ResourceGroupsRowEditor';
// deployment policies
import DeploymentPoliciesTemplate from 'DeploymentPoliciesTemplate';
import DeploymentPoliciesRowRenderers from
    'components/deploymentpolicies/DeploymentPoliciesRowRenderers';
import DeploymentPoliciesRowEditor from 'components/deploymentpolicies/DeploymentPoliciesRowEditor';

import { ResourcePoolsActions, CredentialsActions, CertificatesActions,
  ResourceGroupsActions, DeploymentPolicyActions } from 'actions/Actions';

var InlineEditableListFactory = {
  createResourcePoolsList: function($el) {
    var list = new InlineEditableList($el, ResourcePoolsTemplate, ResourcePoolsRowRenderers);
    list.setRowEditor(ResourcePoolsRowEditor);
    list.setDeleteCallback(ResourcePoolsActions.deleteResourcePool);
    list.setEditCallback(ResourcePoolsActions.editResourcePool);

    return list;
  },

  createCredentialsList: function($el) {
    var list = new InlineEditableList($el, CredentialsTemplate, CredentialsRowRenderers);

    list.setRowEditor(CredentialsRowEditor);
    list.setDeleteCallback(CredentialsActions.deleteCredential);
    list.setEditCallback(CredentialsActions.editCredential);

    return list;
  },

  createCertificatesList: function($el) {
    var list = new InlineEditableList($el, CertificatesTemplate, CertificatesRowRenderers);

    list.setRowEditor(CertificatesRowEditor);
    list.setDeleteCallback(CertificatesActions.deleteCertificate);
    list.setEditCallback(CertificatesActions.editCertificate);

    return list;
  },

  createGroupsList: function($el) {
    var list = new InlineEditableList($el, ResourceGroupsTemplate, ResourceGroupsRowRenderers);

    list.setRowEditor(ResourceGroupsRowEditor);
    list.setDeleteCallback(ResourceGroupsActions.deleteGroup);
    list.setEditCallback(ResourceGroupsActions.editGroup);

    return list;
  },

  createDeploymentPoliciesList: function($el) {
    var list = new InlineEditableList($el, DeploymentPoliciesTemplate,
        DeploymentPoliciesRowRenderers);

    list.setRowEditor(DeploymentPoliciesRowEditor);
    list.setDeleteCallback(DeploymentPolicyActions.deleteDeploymentPolicy);
    list.setEditCallback(DeploymentPolicyActions.editDeploymentPolicy);

    return list;
  }
};

export default InlineEditableListFactory;
