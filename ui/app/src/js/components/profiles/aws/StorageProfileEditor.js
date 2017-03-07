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

export default Vue.component('aws-storage-profile-editor', {
  template: `
    <div></div>
  `,
  props: {
    endpoint: {
      required: false,
      type: Object
    },
    model: {
      required: true,
      type: Object
    }
  },
  data() {
    return {
    };
  },
  attached() {
    this.emitChange();
  },
  methods: {
    emitChange() {
      this.$emit('change', {
        properties: {},
        valid: true
      });
    }
  }
});
