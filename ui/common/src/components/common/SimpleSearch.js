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

const $ = require('jquery');
require('wamda-typeahead');

const SEARCH_RESULT_LIMIT = 20;

module.exports = class SimpleSearch {

  constructor(displayPropertyName, sourceCallback, selectionCallback) {
    this.$el = $('<div><input type="text"/></div>');

    this.$el.find('input').typeahead({ minLength: 0 }, {
      name: 'simple-search',
      limit: SEARCH_RESULT_LIMIT,
      source: sourceCallback,
      display: displayPropertyName, // e.g. 'id' or 'name'

      templates: {
        suggestion: function(context) {
          var name = context.name || '';
          var query = context._query || '';
          var start = name.indexOf(query);
          var end = start + query.length;
          var prefix = '';
          var suffix = '';
          var root = '';

          if (start > 0) {
            prefix = name.substring(0, start);
          }
          if (end < name.length) {
            suffix = name.substring(end, name.length);
          //   if (context.instances) {
          //     suffix += ' <span class="volume-search-item-secondary">(' + context.instances
          //       + ' ' + i18n.t('app.template.details.editVolume.showingInstances') + ')</span>';
          //   }
          }
          root = name.substring(start, end);

          return `
              <div>
                <div class="search-item">
                  ${prefix}<strong>${root}</strong>${suffix}
                </div>
              </div>`;
        },
        // footer: function(q) {
        //   if (q.suggestions && q.suggestions.length > 0 && typeaheadSource.lastResult) {
        //     var i18nOption = {
        //       count: q.suggestions.length,
        //       totalCount: typeaheadSource.lastResult.totalCount
        //     };
        //     var label = i18n.t('app.template.details.editVolume.showingCount', i18nOption);
        //     return `<div class="tt-options-hint">${label}</div>`;
        //   }
        // },
        notFound: function() {
          // var label = i18n.t('app.template.details.editVolume.noResults');
          return `<div class="tt-options-hint">Not Found</div>`; //${label}
        }
      }
    }).on('typeahead:selected', selectionCallback);
  }

  getEl() {
    return this.$el[0];
  }

  setValue(value) {
    $(this.$el).typeahead('val', value);
  }

  getValue() {
    return $(this.$el).typeahead('val');
  }
}
