import RequestGraphGeneralInfoItemVue from 'components/requests/RequestGraphGeneralInfoItemVue.html'; //eslint-disable-line

import utils from 'core/utils'; //eslint-disable-line

var RequestGraphGeneralInfoItemVueComponent = Vue.extend({
  template: RequestGraphGeneralInfoItemVue,

  props: {
    model: { required: true }
  },

  computed: {
    hasResourceDescription: function() {
      return this.model && this.model.resourceDescription;
    },

    isCluster: function() {
      return this.hasResourceDescription && this.model.resourceDescription._cluster > 1;
    },

    hasAffinityRules: function() {
      return this.hasResourceDescription && this.model.resourceDescription.affinity
        && this.model.resourceDescription.affinity.length > 0;
    },

    hasError: function() {
      return this.model.request && this.model.request.taskInfo
        && this.model.request.taskInfo.stage === 'FAILED';
    }
  },

  methods: {
    displayOperation: function(op) {
      if (op === 'PROVISION_RESOURCE') {
        return 'Provision';
      } else if (op === 'CONFIGURE_HOST') {
        return 'Configure Host';
      } else if (op === 'REMOVE_RESOURCE') {
        return 'Removal';
      }

      return op;
    },

    displayResourceType: function(resType) {
      if (resType === 'DOCKER_CONTAINER') {
        return 'Container';
      } else if (resType === 'COMPOSITE_COMPONENT') {
        return 'Application';
      } else if (resType === 'NETWORK') {
        return 'Network';
      } else if (resType === 'CONFIGURE_HOST') {
        return 'Host Configuration';
      }

      return resType;
    },

    getHostName: function(host) {
      return host && utils.getHostName(host);
    }
  }
});

Vue.component('request-graph-general-info', RequestGraphGeneralInfoItemVueComponent);

export default RequestGraphGeneralInfoItemVueComponent;
