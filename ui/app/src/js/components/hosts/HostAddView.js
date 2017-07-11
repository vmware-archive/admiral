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

import { DropdownSearchMenu } from 'admiral-ui-common';
import HostAddViewVue from 'components/hosts/HostAddViewVue.html';
import PlacementZonesView from 'components/placementzones/PlacementZonesView'; //eslint-disable-line
import CredentialsList from 'components/credentials/CredentialsList'; //eslint-disable-line
import CertificatesList from 'components/certificates/CertificatesList'; //eslint-disable-line
import DeploymentPoliciesList from 'components/deploymentpolicies/DeploymentPoliciesList'; //eslint-disable-line
import AcceptCertificate from 'components/hosts/AcceptCertificate'; //eslint-disable-line
import MulticolumnInputs from 'components/common/MulticolumnInputs';
import Tags from 'components/common/Tags';
import { HostActions, HostContextToolbarActions } from 'actions/Actions';
import constants from 'core/constants';
import utils from 'core/utils';
import ft from 'core/ft';

const placementZoneManageOptions = [{
  id: 'rp-create',
  name: i18n.t('app.placementZone.createNew'),
  icon: 'plus'
}, {
  id: 'rp-manage',
  name: i18n.t('app.placementZone.manage'),
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
      placementZone: null,
      credential: null,
      deploymentPolicy: null,
      connectionType: 'API',
      autoConfigure: false,
      selectedHostType: constants.HOST.TYPE.DOCKER,
      schedulerPlacementZoneName: null,
      allowAcceptCertificateDialog: false
    };
  },

  computed: {
    buttonsDisabled: function() {
      return !this.address;
    },
    isVerifyButtonDisabled: function() {
      return this.buttonsDisabled;
    },
    validationErrors: function() {
      return this.model.validationErrors || {};
    },
    autoConfigurationEnabled: function() {
      return !utils.isApplicationEmbedded() && (!this.model || !this.model.isUpdate);
    },
    isDockerHost: function() {
      return this.selectedHostType === constants.HOST.TYPE.DOCKER;
    },
    isVerifiedDockerHost: function() {
      return this.isDockerHost && this.isHostModelVerified;
    },
    isVerifiedSchedulerHost: function() {
      return !this.isDockerHost && this.isHostModelVerified;
    },
    isHostModelVerified: function() {
      // if it is update and credentials have not changed, return true
      if (this.model.isUpdate && this.model.dto) {
        // We don't have credentials in the saved host - if we don't have in the current - it's ok
        if (!this.model.dto.customProperties.__authCredentialsLink && !this.credential) {
          return true;
        }

        // We have credentials in the saved host - if they are the same as the current - ok
        if (this.credential && this.model.dto.customProperties.__authCredentialsLink
                                === this.credential.documentSelfLink) {
          return true;
        }
      }

      // check if model is verified
      if (!this.model.verifiedHostModel) {
        return false;
      }

      // check if address matches verified address
      if (utils.populateDefaultSchemeAndPort(this.address) !==
          utils.populateDefaultSchemeAndPort(this.model.verifiedHostModel.address)) {
        return false;
      }

      // check if credentials match verified credentials
      if (this.credential) {
        if (!this.model.verifiedHostModel.credential) {
          return false;
        } else if (this.credential.documentSelfLink !==
                   this.model.verifiedHostModel.credential.documentSelfLink) {
          return false;
        }
      } else if (this.model.verifiedHostModel.credential) {
        return false;
      }

      // check if host type matches verified host type
      return this.selectedHostType === this.model.verifiedHostModel.hostType;
    },
    acceptCertificateData: function() {
      return this.model.shouldAcceptCertificate
                && this.model.shouldAcceptCertificate.certificateHolder;
    },
    isVchOptionEnabled: function() {
      return ft.isVchHostOptionEnabled();
    },
    isVic: function() {
      return utils.isVic();
    },
    isKubernetesOptionEnabled: function() {
      return ft.isKubernetesHostOptionEnabled();
    },
    showAllCommonInputs: function() {
      return this.isHostModelVerified || this.model.isUpdate;
    },
    showPlacementZone: function() {
      return this.model.isUpdate || this.isVerifiedDockerHost;
    },
    showDeploymentPolicy: function() {
      return this.showAllCommonInputs;
    },
    showTags: function() {
      return this.isVerifiedDockerHost || (this.model.isUpdate && this.isDockerHost);
    },
    showAutoConfigure: function() {
      return this.isDockerHost && !this.model.isUpdate;
    },
    showCustomProperties: function() {
      return this.showAllCommonInputs;
    },
    showAddButton: function() {
      return this.isHostModelVerified;
    }
  },

  attached: function() {
    // Resource pool input
    var elemPlacementZone = $(this.$el).find('#placementZone .dropdown-holder');
    this.placementZoneInput = new DropdownSearchMenu(elemPlacementZone, {
      title: i18n.t('dropdownSearchMenu.title', {
        entity: i18n.t('app.placementZone.entity')
      }),
      searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
        entity: i18n.t('app.placementZone.entity')
      })
    });
    this.placementZoneInput.setManageOptions(placementZoneManageOptions);
    this.placementZoneInput.setManageOptionSelectCallback(function(option) {
      if (option.id === 'rp-create') {
        HostContextToolbarActions.createPlacementZone();
      } else {
        HostContextToolbarActions.managePlacementZones();
      }
    });
    this.placementZoneInput.setOptionSelectCallback((option) => {
      this.placementZone = option;
    });
    this.placementZoneInput.setClearOptionSelectCallback(() => {
      this.placementZone = undefined;
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

    this.unwatchPlacementZones = this.$watch('model.placementZones', () => {
      if (this.model.placementZones === constants.LOADING) {
        this.placementZoneInput.setLoading(true);
      } else {
        this.placementZoneInput.setLoading(false);
        this.placementZoneInput.setOptions(
            (this.model.placementZones || []).map((config) => {
              if (config.placementZoneType
                  && config.placementZoneType !== constants.PLACEMENT_ZONE.TYPE.DOCKER) {
                let placementZone = config.resourcePoolState.asMutable();
                placementZone.isDisabled = true;
                return placementZone;
              }
              return Immutable(config.resourcePoolState);
            }));
      }
    }, {immediate: true});

    this.unwatchPlacementZone = this.$watch('model.placementZone',
                                           (placementZone, oldPlacementZone) => {
      if (this.model.placementZone && placementZone !== oldPlacementZone) {
        this.placementZoneInput.setSelectedOption(this.model.placementZone.resourcePoolState);
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
          this.disableInput('address', 'input', model.address);
        }

        if (model.hostAlias !== oldModel.hostAlias) {
          this.hostAlias = model.hostAlias;
        }

        if (model.selectedHostType !== oldModel.selectedHostType) {
          this.selectedHostType = model.selectedHostType;
          this.disableInput('hostType', 'div', model.selectedHostType);
        }

        if (model.placementZone !== oldModel.placementZone) {
          this.placementZone = model.placementZone;
          if (model.selectedHostType === constants.HOST.TYPE.DOCKER) {
            this.placementZoneInput.setSelectedOption(model.placementZone);
          } else {
            this.schedulerPlacementZoneName = model.placementZone.name;
          }
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
  },

  detached: function() {
    this.unwatchPlacementZones();
    this.unwatchPlacementZone();

    this.unwatchCredentials();
    this.unwatchCredential();

    this.unwatchDeploymentPolicies();
    this.unwatchDeploymentPolicy();

    this.unwatchModel();
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

    disableInput: function(inputId, elemType, value) {
      var inputEl = $(this.$el).find('#' + inputId + ' ' + elemType);
      inputEl.replaceWith($('<label data-name="host-edit-value">'
                            + value + '</label>'));
    },

    getHostData: function() {
      var hostData = {
        dto: this.model.dto,
        id: this.model.id,
        address: validator.trim(this.address),
        hostType: this.selectedHostType,
        hostAlias: this.hostAlias,
        credential: this.credential,
        connectionType: this.connectionType,
        customProperties: this.customPropertiesEditor.getData(),
        descriptionLink: this.model.descriptionLink,
        selfLinkId: this.model.selfLinkId
      };

      if (this.isDockerHost || this.model.isUpdate) {
        hostData.resourcePoolLink = this.placementZone ?
          this.placementZone.documentSelfLink : null;
      }

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

      var hostData = this.getHostData();
      if (this.autoConfigure) {
        hostData.isConfigureOverSsh = true;
      }

      this.allowAcceptCertificateDialog = true;

      HostActions.verifyHost(hostData);
    },
    saveHost: function() {
      let hostData = this.getHostData();

      // Host auto configuration
      if (this.autoConfigure && this.isDockerHost) {
        // full uri expected
        hostData.address = utils.populateDefaultSchemeAndPort(this.address);
        HostActions.autoConfigureHost(hostData);
        return;
      }

      // tags are supported only for docker hosts
      let tags = this.isDockerHost ? this.tagsInput.getValue() : [];

      // By default try to add the host at the given address.
      // If the host has self signed certificate, we may need to accept it,
      // by clicking confirmAddHost
      if (this.model.isUpdate) {
        var tagRequest = utils.createTagAssignmentRequest(this.model.dto.documentSelfLink,
            this.model.tags, tags);
        HostActions.updateHost(hostData, tagRequest);
      } else {
        HostActions.addHost(hostData, tags);
      }
    },

    confirmAcceptCertificate: function() {
      this.allowAcceptCertificateDialog = false;

      let hostModel = this.getHostData();
      let tags = this.tagsInput.getValue();

      if (this.model.shouldAcceptCertificate.verify) {
        HostActions.acceptCertificateAndVerifyHost(this.acceptCertificateData, hostModel, tags);
      } else {
        HostActions.acceptCertificateAndAddHost(this.acceptCertificateData, hostModel, tags);
      }
    },

    cancelAcceptCertificate: function() {
      this.allowAcceptCertificateDialog = false;
    }
  },
  events: {
    'manage-certificates': function() {
      HostContextToolbarActions.manageCertificates();

      this.allowAcceptCertificateDialog = false;
    }
  }
});

Vue.component('host-add-view', HostAddView);

export default HostAddView;
