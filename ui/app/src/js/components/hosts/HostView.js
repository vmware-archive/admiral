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

import HostViewVue from 'HostViewVue';
import HostAddViewVue from 'HostAddViewVue'; //eslint-disable-line
import HostFieldsMixin from 'components/hosts/HostFieldsMixin';
import HostCreateViewVue from 'HostCreateViewVue'; //eslint-disable-line
import ResourcePoolsList from 'components/resourcepools/ResourcePoolsList'; //eslint-disable-line
import CredentialsList from 'components/credentials/CredentialsList'; //eslint-disable-line
import CertificatesList from 'components/certificates/CertificatesList'; //eslint-disable-line
import DeploymentPoliciesList from 'components/deploymentpolicies/DeploymentPoliciesList'; //eslint-disable-line
import HostCertificateConfirmTemplate from 'HostCertificateConfirmTemplate';
import MulticolumnInputs from 'components/common/MulticolumnInputs';
import { HostActions, HostContextToolbarActions, NavigationActions } from 'actions/Actions';
import utils from 'core/utils';
import modal from 'core/modal';

// Constants
const TAB_ID_ADD_HOST = 'AddHost';
const TAB_ID_CREATE_HOST = 'CreateHost';

// The Host View component
var HostView = Vue.extend({
  template: HostViewVue,

  props: {
    model: {
      required: true,
      type: Object,
      default: () => {
        return {
          contextView: {},
          resourcePools: {},
          credentials: {},
          deploymentPolicies: {},
          certificates: {},
          shouldAcceptCertificate: null,
          validationErrors: null,
          isUpdate: false,
          id: null,
          address: null,
          resourcePool: null,
          credential: null,
          connectionType: 'API'
        };
      }
    }
  },
  computed: {
    showTabAddHost: function() {
      return this.selectedTab === TAB_ID_ADD_HOST;
    },
    showTabCreateHost: function() {
      return !this.model.isUpdate && (this.selectedTab === TAB_ID_CREATE_HOST);
    },
    createHostEnabled: function() {
      return utils.getConfigurationProperty('host.provisioning') === 'true';
    },
    activeContextItem: function() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded: function() {
      return this.model.contextView && this.model.contextView.expanded;
    },
    validationErrors: function() {
      return this.model.validationErrors || {};
    }
  },

  data: function() {
    return {
      selectedTab: TAB_ID_ADD_HOST
    };
  },

  components: {

    'host-add-view': {
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
          connectionType: 'API'
        };
      },
      computed: {
        buttonsDisabled: function() {
          return !(this.address && this.resourcePool);
        }
      },

      mixins: [HostFieldsMixin],

      attached: function() {

        this.initializeHostFields();

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

            if (model.connectionType !== oldModel.connectionType) {
              this.connectionType = model.connectionType;
              this.disableInput('connectionType', model.connectionType);
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

          var _this = this;
          $certificateConfirm.find('.confirmAddHost').click(function(e) {
            e.preventDefault();
            let hostModel = _this.getHostData();
            let tags = _this.tagsInput.getValue();
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
            address: this.address,
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
          // By default try to add the host at the given address.
          // If the host has self signed certificate, we may need to accept it,
          // by clicking confirmAddHost
          let hostData = this.getHostData();
          let tags = this.tagsInput.getValue();
          if (this.model.isUpdate) {
            HostActions.updateHost(hostData, tags);
          } else {
            HostActions.addHost(hostData, tags);
          }
        }
      }
    },

    'host-create-view': {
      template: HostCreateViewVue,

      props: {
        model: {
          required: true,
          type: Object
        }
      },

      data: function() {
        return {
          providerType: 'AWS',
          zoneId: 'us-east-1',
          resourcePool: null,
          credential: null,
          instanceType: 't2.micro',
          clusterSize: 1
        };
      },

      mixins: [HostFieldsMixin],

      attached: function() {
        this.initializeHostFields();
      },

      detached: function() {

        this.unwatchResourcePools();
        this.unwatchResourcePool();

        this.unwatchCredentials();
        this.unwatchCredential();

        this.unwatchDeploymentPolicies();
        this.unwatchDeploymentPolicy();

      },

      methods: {
        modifyClusterSize: function($event, incrementValue) {
          $event.stopPropagation();
          $event.preventDefault();

          this.clusterSize += incrementValue;
        },

        getHostData: function() {
          let hostData = {
            providerType: this.providerType,
            zoneId: this.zoneId,
            resourcePool: this.resourcePool,
            credential: this.credential,
            instanceType: this.instanceType,
            clusterSize: this.clusterSize,
            deploymentPolicy: this.deploymentPolicy ? this.deploymentPolicy.documentSelfLink : null
          };

          return hostData;
        },

        createHost: function() {
          HostActions.createHost(this.getHostData());
        }
      }
    }
  },

  methods: {

    goBack: function() {
      NavigationActions.openHosts();
    },

    selectTab: function($event, tabId) {
      $event.preventDefault();
      $event.stopPropagation();

      let leftTab = $(this.$el).find('.view-title .left');
      let rightTab = $(this.$el).find('.view-title .right');

      if (tabId === 'leftTab') {
        // Left tab is selected
        leftTab.addClass('selected');
        rightTab.removeClass('selected');
        this.selectedTab = TAB_ID_ADD_HOST;

      } else if (tabId === 'rightTab') {
        // Right tab is selected
        rightTab.addClass('selected');
        leftTab.removeClass('selected');

        this.selectedTab = TAB_ID_CREATE_HOST;
      }
    },

    openToolbarResourcePools: HostContextToolbarActions.openToolbarResourcePools,
    openToolbarCredentials: HostContextToolbarActions.openToolbarCredentials,
    openToolbarCertificates: HostContextToolbarActions.openToolbarCertificates,
    openToolbarDeploymentPolicies: HostContextToolbarActions.openToolbarDeploymentPolicies,
    closeToolbar: HostContextToolbarActions.closeToolbar
  }
});

Vue.component('host-view', HostView);

export default HostView;
