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

import HostCreateViewVue from 'components/hosts/HostCreateViewVue.html';
import EndpointsView from 'components/endpoints/EndpointsView'; //eslint-disable-line
import MulticolumnInputs from 'components/common/MulticolumnInputs';
import DropdownSearchMenu from 'components/common/DropdownSearchMenu';
import Tags from 'components/common/Tags';
import { HostActions, HostContextToolbarActions } from 'actions/Actions';
import constants from 'core/constants';
import utils from 'core/utils';

const endpointManageOptions = [{
  id: 'endpoint-create',
  name: i18n.t('app.endpoint.createNew'),
  icon: 'plus'
}, {
  id: 'endpoint-manage',
  name: i18n.t('app.endpoint.manage'),
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

// The Host Create View component
var HostCreateView = Vue.extend({

  template: HostCreateViewVue,

  props: {
    model: {
      required: true,
      type: Object
    }
  },

  data: function() {
    return {
      name: null,
      endpoint: null,
      awsType: 't2.micro',
      awsOS: 'coreos',
      azureType: 'Basic_A1',
      azureOS: 'coreos',
      vsphereCpu: 1,
      vsphereMemory: 1024,
      vsphereOS: 'coreos',
      clusterSize: 1
    };
  },

  computed: {
    buttonsDisabled: function() {
      if (!this.name || !this.endpoint) {
        return true;
      }
      switch (this.endpoint.endpointType) {
        case 'aws':
          return !this.awsType && !this.awsOS;
        case 'azure':
          return !this.azureType && !this.azureOS;
        case 'vsphere':
          return !this.vsphereCpu && !this.vsphereMemory &&
            !this.vsphereOS;
      }
    },
    validationErrors: function() {
      return this.model.validationErrors || {};
    }
  },

  attached: function() {

    // Endpoint input
    var elemEndpoint = $(this.$el).find('#endpoint .form-control');
    this.endpointInput = new DropdownSearchMenu(elemEndpoint, {
      title: i18n.t('dropdownSearchMenu.title', {
        entity: i18n.t('app.endpoint.entity')
      }),
      searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
        entity: i18n.t('app.endpoint.entity')
      })
    });
    this.endpointInput.setManageOptions(endpointManageOptions);
    this.endpointInput.setManageOptionSelectCallback(function(option) {
      if (option.id === 'endpoint-create') {
        HostContextToolbarActions.createEndpoint();
      } else {
        HostContextToolbarActions.manageEndpoints();
      }
    });
    this.endpointInput.setOptionSelectCallback((option) => {
      this.endpoint = option;
    });
    this.endpointInput.setClearOptionSelectCallback(() => {
      this.endpoint = null;
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
      this.credential = null;
    });

    this.tagsInput = new Tags($(this.$el).find('#tags .tags-input'));

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

    this.unwatchEndpoints = this.$watch('model.endpoints', () => {
      if (this.model.endpoints === constants.LOADING) {
        this.endpointInput.setLoading(true);
      } else {
        this.endpointInput.setLoading(false);
        this.endpointInput.setOptions(this.model.endpoints);
      }
    }, {immediate: true});

    this.unwatchEndpoint = this.$watch('model.endpoint',
                                               (endpoint, oldEndpoint) => {
      if (this.model.endpoint && endpoint !== oldEndpoint) {
        this.endpointInput.setSelectedOption(this.model.endpoint);
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

    $(this.$el).find('.fa-question-circle').tooltip({html: true});

  },

  detached: function() {
    this.unwatchEndpoints();
    this.unwatchEndpoint();

    this.unwatchCredentials();
    this.unwatchCredential();
  },

  methods: {
    modifyClusterSize: function($event, incrementValue) {
      $event.stopPropagation();
      $event.preventDefault();

      this.clusterSize += incrementValue;
    },
    showInput: function(type) {
      if (this.endpoint) {
        return this.endpoint.endpointType === type;
      }
      return type === null;
    },
    getHostDescription: function() {
      let customProperties = utils.arrayToObject(this.customPropertiesEditor.getData());
      let hostDescription = {
        authCredentialsLink: this.credential && this.credential.documentSelfLink,
        name: this.name,
        supportedChildren: ['DOCKER_CONTAINER'],
        customProperties: $.extend(customProperties, {
          __endpointLink: this.endpoint.documentSelfLink
        })
      };
      switch (this.endpoint.endpointType) {
        case 'aws':
          return $.extend(true, hostDescription, {
            instanceType: this.awsType,
            customProperties: {
              imageType: this.awsOS
            }
          });
        case 'azure':
          return $.extend(true, hostDescription, {
            instanceType: this.azureType,
            customProperties: {
              imageType: this.azureOS
            }
          });
        case 'vsphere':
          return $.extend(true, hostDescription, {
            cpuCount: this.vsphereCpu,
            totalMemoryBytes: this.vsphereMemory * 1024 * 1024,
            customProperties: {
              imageType: this.vsphereOS
            }
          });
      }
    },
    createHost: function() {
      let tags = this.tagsInput.getValue();
      HostActions.createHost(this.getHostDescription(), this.clusterSize, tags);
    }
  }
});

Vue.component('host-create-view', HostCreateView);

export default HostCreateView;
