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

const MultiColumnCell = Vue.component('multicolumn-cell', {
  template: `
    <li>
      <slot></slot>
    </li>
  `,
  props: {
    name: {
      required: true,
      type: String
    }
  }
});

export default Vue.component('multicolumn-editor', {
  template: `
    <div class="multicolumn-inputs">
      <div class="multicolumn-inputs-list">
        <div class="multicolumn-inputs-list-head" v-if="headers && headers.length">
          <div class="multicolumn-input-controls">
            <ul>
              <li v-for="item in headers" track-by="$index">
                <div class="multicolumn-header">
                  <div class="multicolumn-header-label">
                    {{item}}
                  </div>
                </div>
              </li>
            </ul>
          </div>
        </div>
        <div class="multicolumn-inputs-list-body-wrapper">
          <div class="multicolumn-inputs-list-body">
            <div class="multicolumn-input" v-for="item in value" track-by="$index">
              <div class="multicolumn-input-controls">
                <ul>
                  <slot></slot>
                </ul>
              </div>
              <div class="multicolumn-input-toolbar" v-if="!disabled">
                <a href="#" tabindex="-1" class="multicolumn-input-remove"
                    @click="remove($event, $index)">
                  <i class="btn btn-circle fa fa-minus"></i>
                </a>
                <a :style="{'visibility': $index + 1 === value.length ? 'visible' : 'hidden'}"
                    href="#" class="multicolumn-input-add"
                    @click="add($event)">
                  <i class="btn btn-circle fa fa-plus"></i>
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  props: {
    disabled: {
      required: false,
      type: Boolean
    },
    headers: {
      required: false,
      type: Array
    },
    value: {
      required: true,
      type: Array
    }
  },
  attached() {
    if (this.value.length === 0) {
      this.value = [{}];
    }
    Vue.nextTick(() => {
      this.render(0);
    });
  },
  methods: {
    render(start) {
      let children = this.$children;
      if (children.length === 0) {
        children = this.$parent.$parent.$children.filter((child) =>
            child instanceof MultiColumnCell);
      }
      let columns = children.length / this.value.length;
      children.forEach((child, index) => {
        let row = Math.floor(index / columns);
        if (row >= start) {
          child.$children.forEach((comp) => {
            if (comp.value === undefined) {
              comp.$on('change', (value) => {
                this.value[row][child.name] = value;
                this.$emit('change', this.value, this);
              });
            }
            comp.disabled = this.disabled;
            comp.value = this.value[row][child.name];
          });
        }
      });
    },
    add($event) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      this.value = this.value.concat({});
      Vue.nextTick(() => {
        this.render(this.value.length - 1);
      });

      this.$emit('change', this.value, this);
    },
    remove($event, $index) {
      $event.stopImmediatePropagation();
      $event.preventDefault();

      if (this.value.length === 1) {
        return;
      }

      this.value.splice($index, 1);
      Vue.nextTick(() => {
        this.render($index);
      });

      this.$emit('change', this.value, this);
    }
  }
});

