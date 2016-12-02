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
import HostAddViewVue from 'components/hosts/HostAddViewVue.html';
import ResourcePoolsList from 'components/resourcepools/ResourcePoolsList'; //eslint-disable-line
import CredentialsList from 'components/credentials/CredentialsList'; //eslint-disable-line
import CertificatesList from 'components/certificates/CertificatesList'; //eslint-disable-line
import DeploymentPoliciesList from 'components/deploymentpolicies/DeploymentPoliciesList'; //eslint-disable-line
import HostCertificateConfirmTemplate from
  'components/hosts/HostCertificateConfirmTemplate.html';
import MulticolumnInputs from 'components/common/MulticolumnInputs';
import Tags from 'components/common/Tags';
import { HostActions, HostContextToolbarActions } from 'actions/Actions';
import constants from 'core/constants';
import utils from 'core/utils';
import modal from 'core/modal';

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

// The Host Add View component
var HostAddView = Vue.extend({

  template: HostAddViewVue,

  props: {
    model: {
      required: true,
      type: Object
    }
  },

  data: function() {
    return {
      address: null,
      hostAlias: null,
      resourcePool: null,
      credential: null,
      deploymentPolicy: null,
      connectionType: 'API',
      autoConfigure: false
    };
  },

  computed: {
    buttonsDisabled: function() {
      return !this.address;
    },
    isVerifyButtonDisabled: function() {
      return this.buttonsDisabled || this.autoConfigure;
    },
    validationErrors: function() {
      return this.model.validationErrors || {};
    },
    autoConfigurationEnabled: function() {
      return !utils.isApplicationEmbedded() && (!this.model || !this.model.isUpdate);
    }
  },

  attached: function() {
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
    this.resourcePoolInput.setOptionSelectCallback((option) => {
      this.resourcePool = option;
    });
    this.resourcePoolInput.setClearOptionSelectCallback(() => {
      this.resourcePool = undefined;
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
    this.credentialInput.setOptionSelectCallback((option) => {
      this.credential = option;
    });
    this.credentialInput.setClearOptionSelectCallback(() => {
      this.credential = undefined;
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
    this.deploymentPolicyInput.setOptionSelectCallback((option) => {
      this.deploymentPolicy = option;
    });
    this.deploymentPolicyInput.setClearOptionSelectCallback(() => {
      this.deploymentPolicy = undefined;
    });

    this.tagsInput = new Tags($(this.$el).find('#tags .tags-input'));

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

    this.customPropertiesEditor = new MulticolumnInputs(
      $(this.$el).find('#customProperties .custom-prop-fields'), {
        name: {
          header: i18n.t('customProperties.name'),
          placeholder: i18n.t('customProperties.nameHint')
        },
        value: {
          header: i18n.t('customProperties.value'),
          placeholder: i18n.t('customProperties.valueHint')
        }
      });
    $(this.$el).find('#customProperties .custom-prop-fields' +
                      ' .multicolumn-inputs-list-body-wrapper')
      .addClass('scrollable-custom-properties');
    this.customPropertiesEditor.setVisibilityFilter(utils.shouldHideCustomProperty);

    this.unwatchModel = this.$watch('model', (model, oldModel) => {
      if (model.isUpdate) {

        oldModel = oldModel || {};

        if (model.address !== oldModel.address) {
          this.address = model.address;
          this.disableInput('address', model.address);
        }

        if (model.hostAlias !== oldModel.hostAlias) {
          this.hostAlias = model.hostAlias;
        }

        if (model.resourcePool !== oldModel.resourcePool) {
          this.resourcePoolInput.setSelectedOption(model.resourcePool);
          this.resourcePool = this.resourcePoolInput.getSelectedOption();
        }

        if (model.credential !== oldModel.credential) {
          this.credentialInput.setSelectedOption(model.credential);
          this.credential = this.credentialInput.getSelectedOption();
        }

        if (model.deploymentPolicy !== oldModel.deploymentPolicy) {
          this.deploymentPolicyInput.setSelectedOption(model.deploymentPolicy);
          this.deploymentPolicy = this.deploymentPolicyInput.getSelectedOption();
        }

        if (model.tags !== oldModel.tags) {
          this.tagsInput.setValue(model.tags);
          this.tags = this.tagsInput.getValue();
        }

        if (model.customProperties !== oldModel.customProperties) {
          this.customPropertiesEditor.setData(model.customProperties);
        }
      }
    }, {immediate: true});

    // Should accept certificate
    this.unwatchShouldAcceptCertificate = this.$watch('model.shouldAcceptCertificate', () => {
      this.updateCertificateModal(this.model.shouldAcceptCertificate);
    });

    $(this.$el).find('.fa-question-circle').tooltip({html: true});
  },

  detached: function() {
    this.unwatchResourcePools();
    this.unwatchResourcePool();

    this.unwatchCredentials();
    this.unwatchCredential();

    this.unwatchDeploymentPolicies();
    this.unwatchDeploymentPolicy();

    this.unwatchModel();

    this.unwatchShouldAcceptCertificate();
  },

  methods: {
    isApplicationEmbedded: function() {
      return utils.isApplicationEmbedded();
    },
    onBlur: function(e) {
      e.preventDefault();

      var addressInput = $(e.currentTarget);
      addressInput.val(utils.populateDefaultSchemeAndPort(addressInput.val()));
    },
    updateCertificateModal: function(shouldAcceptCertificate) {
      if (shouldAcceptCertificate) {

        this.createAndShowCertificateConfirm(shouldAcceptCertificate.certificateHolder,
          shouldAcceptCertificate.verify);
      } else {

        modal.hide();
      }
    },
    createAndShowCertificateConfirm: function(certificateHolder, isVerify) {
      // TODO make this more vue like with component
      var $certificateConfirm = $(HostCertificateConfirmTemplate(certificateHolder));

      modal.show($certificateConfirm);

      var certificateWarning = i18n.t('app.host.details.certificateWarning', {
        address: this.address
      });
      $certificateConfirm.find('.certificate-warning-text').html(certificateWarning);

      $certificateConfirm.find('.manage-certificates-button').click(function(e) {
        e.preventDefault();
        HostContextToolbarActions.manageCertificates();
        modal.hide();
      });

      $certificateConfirm.find('.show-certificate-btn').click(function(e) {
        e.preventDefault();
        $certificateConfirm.addClass('active');
      });

      $certificateConfirm.find('.hide-certificate-btn').click(function(e) {
        e.preventDefault();
        $certificateConfirm.removeClass('active');
      });

      $certificateConfirm.find('.confirmAddHost').click((e) => {
        e.preventDefault();
        let hostModel = this.getHostData();
        let tags = this.tagsInput.getValue();
        if (isVerify) {
          HostActions.acceptCertificateAndVerifyHost(certificateHolder, hostModel, tags);
        } else {
          HostActions.acceptCertificateAndAddHost(certificateHolder, hostModel, tags);
        }
      });

      $certificateConfirm.find('.confirmCancel').click(function(e) {
        e.preventDefault();
        modal.hide();
      });
    },
    disableInput: function(inputId, value) {
      var inputEl = $(this.$el).find('#' + inputId + ' .col-sm-9');
      inputEl.html('<label class="host-edit-value">' + value + '</label>');
    },

    getHostData: function() {
      var hostData = {
        dto: this.model.dto,
        id: this.model.id,
        address: validator.trim(this.address),
        hostAlias: this.hostAlias,
        resourcePoolLink: this.resourcePool ? this.resourcePool.documentSelfLink : null,
        credential: this.credential,
        connectionType: this.connectionType,
        customProperties: this.customPropertiesEditor.getData(),
        descriptionLink: this.model.descriptionLink,
        selfLinkId: this.model.selfLinkId
      };

      if (this.deploymentPolicy) {
        let policy = hostData.customProperties.find(function(entry) {
          return entry.name === '__deploymentPolicyLink';
        });
        if (policy) {
          let policyIndex = hostData.customProperties.indexOf(policy);
          hostData.customProperties.splice(policyIndex, 1);
        }

        hostData.customProperties.push({
          'name': '__deploymentPolicyLink',
          'value': this.deploymentPolicy.documentSelfLink
        });
      }

      if (hostData.credential) {
        hostData.customProperties = utils.deleteElementFromList(
          hostData.customProperties, '__authCredentialsLink');
        hostData.customProperties.push({
          'name': '__authCredentialsLink',
          'value': hostData.credential.documentSelfLink
        });
      }

      return hostData;
    },
    verifyHost: function(event) {
      event.preventDefault();

      HostActions.verifyHost(this.getHostData());
    },
    saveHost: function() {
      let hostData = this.getHostData();

      // Host auto configuration
      if (this.autoConfigure) {
        // full uri expected
        hostData.address = utils.populateDefaultSchemeAndPort(this.address);
        HostActions.autoConfigureHost(hostData);
        return;
      }

      // By default try to add the host at the given address.
      // If the host has self signed certificate, we may need to accept it,
      // by clicking confirmAddHost
      let tags = this.tagsInput.getValue();
      if (this.model.isUpdate) {
        HostActions.updateHost(hostData, tags);
      } else {
        HostActions.addHost(hostData, tags);
      }
    }
  }
});

Vue.component('host-add-view', HostAddView);

export default HostAddView;
