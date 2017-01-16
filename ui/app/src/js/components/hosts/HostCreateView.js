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
import EndpointsList from 'components/endpoints/EndpointsList'; //eslint-disable-line
import MulticolumnInputs from 'components/common/MulticolumnInputs';
import DropdownSearchMenu from 'components/common/DropdownSearchMenu';
import Tags from 'components/common/Tags';
import { HostActions, HostContextToolbarActions } from 'actions/Actions';
import constants from 'core/constants';
import services from 'core/services';
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

const INITIAL_FILTER = '';
const HOST_RESULT_LIMIT = 10;

function hostRenderer(host) {
  return `
    <div>
      <div class="host-picker-item-primary" title="${host.name}">${host.name}</div>
      <div class="host-picker-item-secondary" title="${host.customProperties.__computeType}">
        (${host.customProperties.__computeType})
      </div>
    </div>`;
}

function hostSearchCallback(q, callback) {
  services.searchCompute(this.resourcePoolLink,
      q || INITIAL_FILTER, HOST_RESULT_LIMIT).then((result) => {
    result.items = result.items.map((host) => {
      host.name = utils.getHostName(host);
      return host;
    });
    callback(result);
  });
}

function toggleChanged() {
  this.$dispatch('change', this.destinationInput.getSelectedOption());
}

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
      instanceType: null,
      imageType: null,
      port: constants.COMPUTE.DOCKER_HOST_PORT,
      destination: null,
      clusterSize: 1
    };
  },

  computed: {
    buttonsDisabled: function() {
      if (!this.name || !this.endpoint) {
        return true;
      }
      return !this.instanceType || !this.imageType ||
          (this.endpoint.endpointType === 'vsphere' && !this.destination);
    },
    endpointEnvironment: function() {
      if (this.endpoint && this.model.environments) {
        return this.model.environments.find((environment) =>
            environment.endpointType === this.endpoint.endpointType);
      }
    },
    instanceTypes: function() {
      return this.endpointEnvironment &&
          this.endpointEnvironment.computeProfile.instanceTypeMapping;
    },
    imageTypes: function() {
      return this.endpointEnvironment &&
          this.endpointEnvironment.computeProfile.imageMapping;
    },
    validationErrors: function() {
      return this.model.validationErrors || {};
    },
    isNimbusEndpoint: function() {
      return this.endpoint && this.endpoint.endpointType === 'nimbus';
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
      this.instanceType = null;
      this.imageType = null;
      this.destination = null;
      Vue.nextTick(() => {
        this.instanceType = Object.keys(this.instanceTypes)[0];
        this.imageType = Object.keys(this.imageTypes)[0];
      });
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

  components: {
    destinationSearch: {
      template: '<div></div>',
      props: {
        resourcePoolLink: {
          required: true,
          type: String
        }
      },
      attached: function() {
        this.destinationInput = new DropdownSearchMenu($(this.$el), {
          title: i18n.t('dropdownSearchMenu.title', {
            entity: i18n.t('app.compute.entity')
          }),
          searchPlaceholder: i18n.t('app.host.details.destinationPlaceholder')
        });
        this.destinationInput.setOptionsRenderer(hostRenderer);
        this.destinationInput.setOptionSelectCallback(() => toggleChanged.call(this));
        this.destinationInput.setClearOptionSelectCallback(() => toggleChanged.call(this));
        this.destinationInput.setFilterCallback(hostSearchCallback.bind(this));
        this.destinationInput.setFilter(INITIAL_FILTER);
      }
    }
  },

  methods: {
    onDestinationChange: function(destination) {
      this.destination = destination ? destination.documentSelfLink : null;
    },
    modifyClusterSize: function($event, incrementValue) {
      $event.stopPropagation();
      $event.preventDefault();

      if (this.clusterSize === 1 && incrementValue < 0) {
        return;
      }
      this.clusterSize += incrementValue;
    },
    getInstanceTypeDescription: function(key) {
      if (!utils.isApplicationEmbedded()) {
        return i18n.t(`app.environment.instanceType.${this.endpoint.endpointType}.${key}`);
      }
      return key;
    },
    getImageTypeDescription: function(key) {
      if (!utils.isApplicationEmbedded()) {
        return i18n.t(`app.environment.imageType.${key}`);
      }
      return key;
    },
    getHostDescription: function() {
      let customProperties = utils.arrayToObject(this.customPropertiesEditor.getData());
      let hostDescription = {
        authCredentialsLink: this.credential && this.credential.documentSelfLink,
        name: this.name,
        supportedChildren: ['DOCKER_CONTAINER'],
        customProperties: $.extend(customProperties, {
          __endpointLink: this.endpoint.documentSelfLink,
          __dockerHostPort: parseInt(this.port || constants.COMPUTE.DOCKER_HOST_PORT, 10)
        })
      };
      return $.extend(true, hostDescription, {
        instanceType: this.instanceType,
        customProperties: {
          imageType: this.imageType,
          __placementLink: this.destination
        }
      });
    },
    createHost: function() {
      let tags = this.tagsInput.getValue();
      HostActions.createHost(this.getHostDescription(), this.clusterSize, tags);
    }
  }
});

Vue.component('host-create-view', HostCreateView);

export default HostCreateView;
