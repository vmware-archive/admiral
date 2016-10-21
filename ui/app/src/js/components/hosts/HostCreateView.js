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

import HostCreateViewVue from 'HostCreateViewVue'; //eslint-disable-line
import EndpointsView from 'components/endpoints/EndpointsView'; //eslint-disable-line
import MulticolumnInputs from 'components/common/MulticolumnInputs';
import DropdownSearchMenu from 'components/common/DropdownSearchMenu';
import Tags from 'components/common/Tags';
import { HostContextToolbarActions } from 'actions/Actions';
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
      awsType: 't2.nano',
      awsOS: 'coreos',
      azureType: 'a2',
      azureOS: 'ubuntu',
      vsphereCpu: 1,
      vsphereMemory: 1024,
      vsphereVmdk: 'none',
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
          return !this.vsphereCpu && !this.vsphereMemory && !this.vsphereVmdk;
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
      this.endpoint = undefined;
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

    this.unwatchModel = this.$watch('model', (model, oldModel) => {
      if (model.isUpdate) {

        oldModel = oldModel || {};

        if (model.endpoint !== oldModel.endpoint) {
          this.endpointInput.setSelectedOption(model.endpoint);
          this.endpoint = this.endpointInput.getSelectedOption();
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

    $(this.$el).find('.fa-question-circle').tooltip({html: true});

  },

  detached: function() {
    this.unwatchEndpoints();
    this.unwatchEndpoint();

    this.unwatchModel();
  },

  methods: {
    modifyClusterSize: function($event, incrementValue) {
      $event.stopPropagation();
      $event.preventDefault();

      this.clusterSize += incrementValue;
    },
    showInput: function(type) {
      return this.endpoint && this.endpoint.endpointType === type;
    },
    getHostData: function() {
      let hostData = {
        name: this.name,
        endpointLink: this.endpoint.documentSelfLink,
        clusterSize: this.clusterSize,
        customProperties: this.customPropertiesEditor.getData()
      };
      switch (this.endpoint.endpointType) {
        case 'aws':
          return $.extend(hostData, {
            type: this.awsType,
            os: this.awsOS
          });
        case 'azure':
          return $.extend(hostData, {
            type: this.azureType,
            os: this.azureOS
          });
        case 'vsphere':
          return $.extend(hostData, {
            cpu: this.vsphereCpu,
            memory: this.vsphereMemory,
            vmdk: this.vsphereVmdk
          });
      }
    },
    createHost: function() {
      //HostActions.createHost(this.getHostData());
      let tags = this.tagsInput.getValue();
      console.log('createHost', this.getHostData(), tags);
    }
  }
});

Vue.component('host-create-view', HostCreateView);

export default HostCreateView;
