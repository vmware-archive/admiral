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

export default Vue.component('typeahead-control', {
  template: `
    <div class="form-control search-input">
      <input
        class="form-control"
        :disabled="disabled"
        :name="name"
        :value="value"
        @change="onChange"
        @input="onChange">
      <i class="fa fa-spinner fa-spin loader-inline form-control-feedback"></i>
      <i class="fa fa-search search-hint form-control-feedback"></i>
    </div>
  `,
  props: {
    disabled: {
      default: false,
      required: false,
      type: Boolean
    },
    display: {
      default: 'name',
      required: false,
      type: String
    },
    limit: {
      default: 10,
      required: false,
      type: Number
    },
    renderer: {
      required: false,
      type: Function
    },
    source: {
      required: false,
      type: Function
    },
    value: {
      required: false,
      type: Object
    },
    showFooter: {
      default: true,
      required: false,
      type: Boolean
    }
  },
  methods: {
    onChange($event) {
      $event.preventDefault();
      $event.stopPropagation();

      this.value = $event.currentTarget.value;
      this.$emit('change', this.value);
    }
  },
  attached() {
    var me = this;
    $(this.$el).find('input').typeahead({
      minLength: 0
    }, {
      limit: this.limit,
      source: (q, sync, async) => {
        this.source.call(this, q || '', this.limit).then((result) => {
          let items = result && result.items;
          if (!items) {
            items = [];
          }
          me.lastResult = result;
          async(items);
        });
      },
      display: this.display,
      templates: {
        suggestion: (context) => {
          if (this.renderer) {
            return this.renderer.call(this, context);
          } else {
            let display = context[this.display] || '';
            let query = context._query || '';
            let value = query ? display.replace(query, '<strong>' + query + '</strong>') : display;
            return `
              <div>
                <div>${value}</div>
              </div>
            `;
          }
        },
        footer: function() {
          if (me.showFooter) {
            var i18nOption = {
              count: Math.min(me.limit, me.lastResult.items.length),
              totalCount: me.lastResult.totalCount
            };
            return `<div class="dropdown-options-hint">
                      ${i18n.t('dropdownSearchMenu.showingCount', i18nOption)}
                    </div>`;
          }
        }
      }
    }).on('typeahead:select', (ev, suggestion) => {
      this.$emit('change', suggestion[this.display]);
    });
    this.unwatchValue = this.$watch('value', (value) => {
      $(this.$el).find('input').typeahead('val', value);
    });
  },
  detached() {
    this.unwatchValue();
  }
});
