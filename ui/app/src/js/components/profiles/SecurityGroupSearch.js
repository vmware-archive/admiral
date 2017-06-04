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

import services from 'core/services';

export default Vue.component('securitygroup-search', {
  template: `
    <div>
      <dropdown-search
        :disabled="disabled"
        :entity="i18n('app.securityGroup.entity')"
        :filter="searchSecurityGroups"
        :value="value"
        @change="onChange">
      </dropdown-search>
    </div>
  `,
  props: {
    disabled: {
      default: false,
      required: false,
      type: Boolean
    },
    endpoint: {
      required: false,
      type: Object
    },
    value: {
      required: false,
      type: Object
    }
  },
  methods: {
    searchSecurityGroups(...args) {
      return new Promise((resolve, reject) => {
        services.searchSecurityGroups.apply(null,
            [this.endpoint.documentSelfLink, ...args]).then((result) => {
          resolve(result);
        }).catch(reject);
      });
    },
    onChange(value) {
      this.value = value;
      this.$emit('change', value);
    }
  }
});
