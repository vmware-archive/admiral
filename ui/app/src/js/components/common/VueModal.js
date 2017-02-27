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
import ModalVue from 'components/common/ModalVue.html';

export default Vue.component('modal', {
  template: ModalVue,
  props: {
    show: {
      required: true,
      type: Boolean,
      default: false
    },
    size: {
      required: false,
      type: String,
      default: ''
    },
    closable: {
      required: false,
      type: Boolean,
      default: true
    },
    hasHeader: {
      required: false,
      type: Boolean,
      default: true
    }
  },
  computed: {
    modalSize: function() {
      if (this.size.length > 0) {
        return 'modal-' + this.size;
      }

      return '';
    }
  }
});
