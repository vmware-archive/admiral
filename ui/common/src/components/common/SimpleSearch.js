/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
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

const SEARCH_RESULT_LIMIT = 50;

/**
 * Component providing simple search typeahead functionality.
 */
module.exports = class SimpleSearch {

  constructor(displayPropertyName, sourceCallback, selectionCallback) {
    this.$el = $('<div class="simple-search-holder"><input type="text"/></div>');

    this.$el.find('input').typeahead({ minLength: 0 }, {
      name: 'simple-search',
      limit: SEARCH_RESULT_LIMIT,
      source: sourceCallback,
      display: displayPropertyName, // e.g. 'id' or 'name'

      templates: {
        suggestion: function(context) {
          var name = context.name || '';
          var query = context._query || '';

          var queryAtIndex = query.lastIndexOf('@');
          if (queryAtIndex > 0) {
            // ignore everything after the last '@' in case of query@domain searches
            query = query.substring(0, queryAtIndex);
          }

          var start = name.indexOf(query);
          var end = start + query.length;
          var prefix = '';
          var suffix = '';
          var root = '';

          if (start > 0) {
            prefix = name.substring(0, start);
            prefix = escapeHtml(prefix);
          }
          if (end < name.length) {
            suffix = name.substring(end, name.length);
            suffix = escapeHtml(suffix);
          }
          root = name.substring(start, end);
          root = escapeHtml(root);

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
        //     var label = i18n.t('infoMessages.showingCount', i18nOption);
        //     return `<div class="tt-options-hint">${label}</div>`;
        //   }
        // },
        notFound: function() {
          var label = 'Not Found'; // TODO i18n.t('infoMessages.notFound')
          return `<div class="tt-options-hint">${label}</div>`;
        }
      }
    }).on('typeahead:selected', selectionCallback);
  }

  getEl() {
    return this.$el[0];
  }

  setValue(value) {
    $(this.getEl()).find('input').typeahead('val', value);
  }

  getValue() {
    return $(this.getEl()).find('input').typeahead('val');
  }
}

function escapeHtml(htmlString) {
    return $('<div>').text(htmlString).html();
}
