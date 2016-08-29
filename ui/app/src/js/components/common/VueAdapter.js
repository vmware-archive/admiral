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

import Component from 'components/common/Component';

class VueAdpater extends Component {
  constructor($el, tagName) {
    super();

    $el.empty();
    this.$selfEl = $('<div>').append($('<' + tagName + '>').attr('v-bind:model', 'model'));
    $el.append(this.$selfEl);
  }

  attached() {
    this.vue = new Vue({
      el: this.$selfEl[0]
    });
  }

  detached() {
    this.vue.$destroy(true);
    this.vue = null;
  }

  setData(newData) {
    Vue.set(this.vue, 'model', newData);
  }
}

export default VueAdpater;
