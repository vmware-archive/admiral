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

import MachineDetailsViewVue from 'components/machines/MachineDetailsViewVue.html';

export default Vue.component('machine-details', {
  template: MachineDetailsViewVue,
  data: function() {
    return {};
  },

  props: {
    model: { required: true }
  },

  computed: {
    hasOperationError() {
      return this.model.operationFailure && (this.model.operationFailure != null);
    },
    hasGeneralError() {
      return this.model.validationErrors && this.model.validationErrors._generic;
    },
    generalError() {
      return this.hasGeneralError ? this.model.validationErrors._generic : '';
    }
  },

  methods: {
    refresh: function() {
    }
  }
});
