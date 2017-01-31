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

import Tags from 'components/common/Tags';

export default Vue.component('tags', {
  template: `
    <input class="tags-input">
  `,
  props: {
    placeholder: {
      required: false,
      type: String
    },
    value: {
      required: false,
      type: Array
    }
  },
  attached: function() {
    let el = $(this.$el);
    if (this.placeholder) {
      el.attr('placeholder', this.placeholder);
    }
    let tags = new Tags(el);
    tags.setValue(this.value);
    tags.setChangeCallback(() => this.$dispatch('change', tags.getValue()));
    this.unwatchValue = this.$watch('value', (value) => {
      tags.setValue(value);
    });
  },
  detached: function() {
    this.unwatchValue();
  }
});
