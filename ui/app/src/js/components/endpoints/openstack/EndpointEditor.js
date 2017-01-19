/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

export default Vue.component('openstack-endpoint-editor', {
  template: `
    <div>
      <text-input
        :disabled="model.documentSelfLink"
        :label="i18n('app.endpoint.edit.openstack.url')"
        :required="true"
        :value="host"
        @change="onHostChange">
      </text-input>
      <text-input
        :disabled="model.documentSelfLink"
        :label="i18n('app.endpoint.edit.openstack.domain')"
        :required="true"
        :value="regionId"
        @change="onDomainChange">
      </text-input>
      <text-input
        :label="i18n('app.endpoint.edit.vsphere.privateKeyIdLabel')"
        :required="true"
        :value="privateKeyId"
        @change="onPrivateKeyIdChange">
      </text-input>
      <password-input
        :label="i18n('app.endpoint.edit.vsphere.privateKeyLabel')"
        :required="true"
        :value="privateKey"
        @change="onPrivateKeyChange">
      </password-input>
      <text-input
        :disabled="model.documentSelfLink"
        :label="i18n('app.endpoint.edit.openstack.project')"
        :required="true"
        :value="openstackProject"
        @change="onProjectChange">
      </text-input>
    </div>
  `,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  data() {
    let properties = this.model.endpointProperties || {};
    return {
      host: properties.host,
      regionId: properties.regionId,
      privateKeyId: properties.privateKeyId,
      privateKey: properties.privateKey,
      openstackProject: properties.openstackProject
    };
  },
  methods: {
    onHostChange(host) {
        this.host = host;
        this.dispatchChangeIfNeeded();
    },
    onDomainChange(domain) {
      this.regionId = domain;
      this.dispatchChangeIfNeeded();
    },
    onPrivateKeyIdChange(privateKeyId) {
      this.privateKeyId = privateKeyId;
      this.dispatchChangeIfNeeded();
    },
    onPrivateKeyChange(privateKey) {
      this.privateKey = privateKey;
      this.dispatchChangeIfNeeded();
    },
    onProjectChange(openstackProject) {
      this.openstackProject = openstackProject;
      this.dispatchChangeIfNeeded();
    },
    dispatchChangeIfNeeded() {
      if (this.privateKeyId && this.privateKey && this.regionId && this.host) {
        this.$dispatch('change', {
          host: this.host,
          regionId: this.regionId,
          openstackDomain: this.regionId,
          privateKeyId: this.privateKeyId,
          privateKey: this.privateKey,
          openstackProject: this.openstackProject,
          identityVersion: 'V3'
        });
      }
    }
  }
});
