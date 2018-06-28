/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import utils from 'core/utils';

import uiCommonLib from 'admiral-ui-common';

/**
 * Tag selector for the grid search component.
 */
var VueGridSearchTag = Vue.extend({
  template: `<div v-if="searchTagName" id="searchTagsSelector"
                    class="form-group search-tags-selection"><div
                    class="form-control dropdown-holder"></div></div>`,
  props: {
    queryOptions: {
      type: Object,
      required: true
    },
    searchTagName: {
      type: String,
      required: false
    },
    searchTagOptions: {
      type: Array,
      required: false
    }
  },

  data: function() {
    return {
      searchTagSelection: undefined
    };
  },

  attached: function() {
    this.unwatchTagOptions = this.$watch('searchTagOptions', () => {
      this.initSearchTagSelection();
    }, { immediate: true });

    this.unwatchQueryOptions = this.$watch('queryOptions', (newQueryOptions) => {
      this.preselectOption(newQueryOptions);
    }, { immediate: true });
  },

  detached: function() {
    this.unwatchTagOptions();
    this.unwatchQueryOptions();
  },

  methods: {
    initSearchTagSelection() {
      if (!this.searchTagName || !this.searchTagOptions) {
        return;
      }

      // search tag input
      var elemSearchTags = $(this.$el).parent().find('#searchTagsSelector .form-control');

      this.searchTagInput = new uiCommonLib.DropdownSearchMenu(elemSearchTags, {
        title: i18n.t('dropdownSearchMenu.title', {
          entity: this.searchTagName
        }),
        searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
          entity: this.searchTagName
        })
      });
      // options
      let options = this.searchTagOptions.map((tagOption) => {
        return {
          name: tagOption,
          value: tagOption
        };
      });
      this.searchTagInput.setOptions(options);

      if (this.queryOptions && this.queryOptions[this.searchTagName]) {
        this.searchTagSelection = {
          name: this.queryOptions[this.searchTagName],
          value: this.queryOptions[this.searchTagName]
        };
      }
      this.searchTagInput.setSelectedOption(this.searchTagSelection);

      // select option
      this.searchTagInput.setOptionSelectCallback((option) => {
        this.searchTagSelection = option;

        this.$dispatch('search-option-select', {
          name: this.searchTagName,
          value: this.searchTagSelection.value
        });
      });

      // clear selection
      this.searchTagInput.setClearOptionSelectCallback(() => {
        this.searchTagSelection = undefined;

        this.$dispatch('search-option-select', null);
      });
    },

    preselectOption: function(queryOptions) {
      if (!this.searchTagInput) {
        return;
      }

      let searchTagValue = queryOptions && queryOptions[this.searchTagName];

      if (searchTagValue) {
        if ($.isArray(searchTagValue) && searchTagValue.length > 0) {
          searchTagValue = searchTagValue[0];
        }

        this.searchTagSelection = {
          name: searchTagValue,
          value: searchTagValue
        };
      } else {
        this.searchTagSelection = null;
      }

      let selectedOption = this.searchTagInput.getSelectedOption();
      if (selectedOption && !utils.equals(selectedOption, this.searchTagSelection)) {

        this.searchTagInput.setSelectedOption(this.searchTagSelection);
      }
    }
  }
});

Vue.component('search-tag-selector', VueGridSearchTag);

export default VueGridSearchTag;
