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

import DropdownSearchMenu from 'components/common/DropdownSearchMenu';
import { HostContextToolbarActions } from 'actions/Actions';
import constants from 'core/constants';

const resourcePoolManageOptions = [{
  id: 'rp-create',
  name: i18n.t('app.resourcePool.createNew'),
  icon: 'plus'
}, {
  id: 'rp-manage',
  name: i18n.t('app.resourcePool.manage'),
  icon: 'pencil'
}];

const credentialManageOptions = [{
  id: 'cred-create',
  name: i18n.t('app.credential.createNew'),
  icon: 'plus'
}, {
  id: 'cred-manage',
  name: i18n.t('app.credential.manage'),
  icon: 'pencil'
}];

const deploymentPolicyManageOptions = [{
  id: 'policy-create',
  name: i18n.t('app.deploymentPolicy.createNew'),
  icon: 'plus'
}, {
  id: 'policy-manage',
  name: i18n.t('app.deploymentPolicy.manage'),
  icon: 'pencil'
}];

var HostFieldsMixin = {
  methods: {
    initializeHostFields: function() {

      // Resource pool input
      var elemResourcePool = $(this.$el).find('#resourcePool .form-control');
      this.resourcePoolInput = new DropdownSearchMenu(elemResourcePool, {
        title: i18n.t('dropdownSearchMenu.title', {
          entity: i18n.t('app.resourcePool.entity')
        }),
        searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
          entity: i18n.t('app.resourcePool.entity')
        })
      });

      this.resourcePoolInput.setManageOptions(resourcePoolManageOptions);
      this.resourcePoolInput.setManageOptionSelectCallback(function(option) {
        if (option.id === 'rp-create') {
          HostContextToolbarActions.createResourcePool();
        } else {
          HostContextToolbarActions.manageResourcePools();
        }
      });

      // Credentials input
      var elemCredentials = $(this.$el).find('#credential .form-control');
      this.credentialInput = new DropdownSearchMenu(elemCredentials, {
        title: i18n.t('dropdownSearchMenu.title', {
          entity: i18n.t('app.credential.entity')
        }),
        searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
          entity: i18n.t('app.credential.entity')
        })
      });

      this.credentialInput.setManageOptions(credentialManageOptions);
      this.credentialInput.setManageOptionSelectCallback(function(option) {
        if (option.id === 'cred-create') {
          HostContextToolbarActions.createCredential();
        } else {
          HostContextToolbarActions.manageCredentials();
        }
      });

      // Deployment policy input
      var deploymentPolicyEl = $(this.$el).find('#deploymentPolicy .form-control');
      this.deploymentPolicyInput = new DropdownSearchMenu(deploymentPolicyEl, {
        title: i18n.t('dropdownSearchMenu.title', {
          entity: i18n.t('app.deploymentPolicy.entity')
        }),
        searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
          entity: i18n.t('app.deploymentPolicy.entity')
        })
      });

      this.deploymentPolicyInput.setManageOptions(deploymentPolicyManageOptions);
      this.deploymentPolicyInput.setManageOptionSelectCallback(function(option) {
        if (option.id === 'policy-create') {
          HostContextToolbarActions.createDeploymentPolicy();
        } else {
          HostContextToolbarActions.manageDeploymentPolicies();
        }
      });

      var _this = this;
      this.resourcePoolInput.setOptionSelectCallback(function(option) {
        _this.resourcePool = option;
      });
      this.resourcePoolInput.setClearOptionSelectCallback(function() {
        _this.resourcePool = undefined;
      });

      this.credentialInput.setOptionSelectCallback(function(option) {
        _this.credential = option;
      });
      this.credentialInput.setClearOptionSelectCallback(function() {
        _this.credential = undefined;
      });

      this.deploymentPolicyInput.setOptionSelectCallback(function(option) {
        _this.deploymentPolicy = option;
      });
      this.deploymentPolicyInput.setClearOptionSelectCallback(function() {
        _this.deploymentPolicy = undefined;
      });

      this.unwatchResourcePools = this.$watch('model.resourcePools', () => {
        if (this.model.resourcePools === constants.LOADING) {
          this.resourcePoolInput.setLoading(true);
        } else {
          this.resourcePoolInput.setLoading(false);
          this.resourcePoolInput.setOptions(
              (this.model.resourcePools || []).map((config) => config.resourcePoolState));
        }
      }, {immediate: true});

      this.unwatchResourcePool = this.$watch('model.resourcePool',
                                             (resourcePool, oldResourcePool) => {
        if (this.model.resourcePool && resourcePool !== oldResourcePool) {
          this.resourcePoolInput.setSelectedOption(this.model.resourcePool.resourcePoolState);
        }
      }, {immediate: true});

      this.unwatchCredentials = this.$watch('model.credentials', () => {
        if (this.model.credentials === constants.LOADING) {
          this.credentialInput.setLoading(true);
        } else {
          this.credentialInput.setLoading(false);
          this.credentialInput.setOptions(this.model.credentials);
        }
      }, {immediate: true});

      this.unwatchCredential = this.$watch('model.credential', (credential, oldCredential) => {
        if (this.model.credential && credential !== oldCredential) {
          this.credentialInput.setSelectedOption(this.model.credential);
        }
      }, {immediate: true});

      this.unwatchDeploymentPolicies = this.$watch('model.deploymentPolicies', () => {
        if (this.model.deploymentPolicies === constants.LOADING) {
          this.deploymentPolicyInput.setLoading(true);
        } else {
          this.deploymentPolicyInput.setLoading(false);
          this.deploymentPolicyInput.setOptions(this.model.deploymentPolicies);
        }
      }, {immediate: true});

      this.unwatchDeploymentPolicy = this.$watch('model.deploymentPolicy',
                                                 (deploymentPolicy, oldDeploymentPolicy) => {
        if (this.model.deploymentPolicy && deploymentPolicy !== oldDeploymentPolicy) {
          this.deploymentPolicyInput.setSelectedOption(this.model.deploymentPolicy);
        }
      }, {immediate: true});
    }
  },
  computed: {
    validationErrors: function() {
      return this.model.validationErrors || {};
    }
  }
};

export default HostFieldsMixin;
