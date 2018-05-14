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

import GridSearchVue from 'components/common/GridSearchVue.html';

import constants from 'core/constants';
import utils from 'core/utils';
import uiCommonLib from 'admiral-ui-common';

/**
 * General search component shown on grid views.
 */
var GridSearch = Vue.extend({
  template: GridSearchVue,

  props: {
    queryOptions: {
      type: Object
    },
    placeholder: {
      type: String,
      required: false
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

  computed: {
    hasSearchTag() {
      return this.searchTagName && this.searchTagOptions;
    }
  },

  data: function() {
    return {
      searchString: '',
      occurrenceSelection: constants.SEARCH_OCCURRENCE.ALL,
      searchTagSelection: ''
    };
  },

  attached: function() {

    this.unwatchQueryOptions = this.$watch('searchTagOptions', () => {
      this.initSearchTagSelection();
    }, { immediate: true });

    this.unwatchQueryOptions = this.$watch('queryOptions', (newQueryOptions, oldQueryOptions) => {
      if (!utils.equals(oldQueryOptions, newQueryOptions)) {
        let oldSearchString = uiCommonLib.searchUtils.getSearchString(oldQueryOptions);
        let oldOccurrenceSelection = uiCommonLib.searchUtils.getOccurrence(oldQueryOptions);

        this.searchString = uiCommonLib.searchUtils.getSearchString(newQueryOptions);
        this.occurrenceSelection = uiCommonLib.searchUtils.getOccurrence(newQueryOptions);
        if (!this.occurrenceSelection) {
          this.occurrenceSelection = constants.SEARCH_OCCURRENCE.ALL;
        }

        if (oldSearchString !== this.searchString
              || oldOccurrenceSelection !== this.occurrenceSelection) {
          this.search();
        }
      }
    }, { immediate: true });
  },

  detached: function() {
    this.unwatchQueryOptions();
  },

  methods: {
    getQueryOptions: function() {
      return this.search.getQueryOptions();
    },

    onSearch: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      this.search();
    },

    search: function() {
      if (this.hasSearchTag && this.searchString === '') {
        this.searchTagSelection = undefined;
        this.searchTagInput.setSelectedOption(this.searchTagSelection);
      }

      let searchQueryOptions =
        uiCommonLib.searchUtils.getQueryOptions(this.searchString, this.occurrenceSelection);

      this.$dispatch('search-grid-action', searchQueryOptions);
    },

    initSearchTagSelection() {

      if (!this.hasSearchTag) {
        return;
      }

      // search tag input
      var elemSearchTags = $(this.$el).find('#searchTagsSelector .form-control');
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
      this.searchTagInput.setSelectedOption(this.searchTagSelection);

      // select option
      this.searchTagInput.setOptionSelectCallback((option) => {
        this.searchTagSelection = option;

        let tagSearchString = this.searchTagName + ':' + this.searchTagSelection.value;
        let idxSearchTag = this.searchString.indexOf(this.searchTagName);
        if (idxSearchTag > -1) {
          // replace query string with new selection
          this.searchString = tagSearchString + ' ';
        } else {
          // add search tag to query string
          if (this.searchString.length > 0) {
            this.searchString += ' ';
          }
          this.searchString += tagSearchString;
        }

        this.search();
      });
      // clear selection
      this.searchTagInput.setClearOptionSelectCallback(() => {
        this.searchTagSelection = undefined;
        this.searchString = '';

        this.search();
      });
    }
  }
});

Vue.component('grid-search', GridSearch);

export default GridSearch;
