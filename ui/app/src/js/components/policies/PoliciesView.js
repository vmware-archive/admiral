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
import PoliciesViewVue from 'PoliciesViewVue';
import PoliciesListTemplate from 'PoliciesListTemplate';
import PoliciesRowRenderers from 'components/policies/PoliciesRowRenderers';
import PoliciesRowEditor from 'components/policies/PoliciesRowEditor';
import ResourcePoolsList from 'components/resourcepools/ResourcePoolsList'; //eslint-disable-line
import ResourceGroupsList from 'components/resourcegroups/ResourceGroupsList'; //eslint-disable-line
import DeploymentPoliciesList from 'components/deploymentpolicies/DeploymentPoliciesList'; //eslint-disable-line
import { PolicyActions, PolicyContextToolbarActions } from 'actions/Actions';
import utils from 'core/utils';

var PoliciesView = Vue.extend({
  template: PoliciesViewVue,
  props: {
    model: {
      required: true,
      type: Object,
      default: () => {
        return {
          policies: {},
          contextView: {}
        };
      }
    }
  },
  computed: {
    activeContextItem: function() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded: function() {
      return this.model.contextView && this.model.contextView.expanded;
    },
    innerContextExpanded: function() {
      var activeItemData = this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.data;
      return activeItemData && activeItemData.contextView && activeItemData.contextView.expanded;
    },
    hasError: function() {
      return this.model.error && this.model.error._generic;
    },
    errorMessage: function() {
      return this.hasError ? this.model.error._generic : '';
    },
    itemsCount: function() {
      return this.model.policies && this.model.policies.items.length;
    }
  },
  attached: function() {
    var $policiesListHolder = $(this.$el).find('.list-holder');
    this.policiesList = new InlineEditableList($policiesListHolder, PoliciesListTemplate,
                                             PoliciesRowRenderers);

    this.policiesList.setRowEditor(PoliciesRowEditor);
    this.policiesList.setDeleteCallback(PolicyActions.deletePolicy);
    this.policiesList.setEditCallback(PolicyActions.editPolicy);

    this.unwatchModel = this.$watch('model.policies', (policies) => {
      this.policiesList.setData(policies);
    });
  },
  detached: function() {
    this.unwatchModel();
  },
  methods: {
    i18n: i18n.t,
    openToolbarResourcePools: PolicyContextToolbarActions.openToolbarResourcePools,
    openToolbarResourceGroups: PolicyContextToolbarActions.openToolbarResourceGroups,
    openToolbarDeploymentPolicies: PolicyContextToolbarActions.openToolbarDeploymentPolicies,
    closeToolbar: PolicyContextToolbarActions.closeToolbar,
    refresh: PolicyActions.openPolicies,
    isStandaloneMode: function() {
      return !utils.isApplicationEmbedded();
    }
  }
});

Vue.component('policies-view', PoliciesView);

export default PoliciesView;
