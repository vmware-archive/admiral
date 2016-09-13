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

import services from 'core/services';

function getValue(tag) {
  if (tag.value && tag.value.indexOf(':') === -1) {
      return tag.key + ':' + tag.value;
  }
  if (tag.value) {
    return tag.value;
  }
  return tag.key;
}

function Tags(el) {
  this.$el = el;
  this.$el.tokenfield({
    createTokensOnBlur: true,
    typeahead: [{
      hint: false
    }, {
      source: (q, sync, async) => {
        services.searchTags(q).then((result) => {
          let values = Object.values(result);
          let suggestions = values.map((tag) => ({
            source: tag,
            value: getValue(tag)
          }));
          if (values.length) {
            if (q.indexOf(':') === -1) {
              suggestions = [{ value: values[0].key + ':'}, ...suggestions];
            }
            if (!values.find((tag) => tag.key === q)) {
              suggestions = [{ value: q + (q.indexOf(':') === -1 ? ':' : '')}, ...suggestions];
            }
          } else {
            suggestions = [{ value: q + (q.indexOf(':') === -1 ? ':' : '')}];
          }
          suggestions.sort((a, b) => a.value === b.value ? 0 : +(a.value > b.value) || -1);
          async(suggestions);
        });
      },
      display: (tag) => {
        return tag.value;
      },
      templates: {
        suggestion: (tag) => {
          return '<div>' + tag.value + '</div>';
        }
      }
    }]
  });
  // Workaround a tokenfield/typeahead issue by manually creating a .tt-hint input
  this.$el.parent().find('.twitter-typeahead')
      .prepend('<input class="tt-hint" style="display:none">');
}

Tags.prototype.getValue = function() {
  return this.$el.tokenfield('getTokens').map((token) => {
    var pair = token.value.split(':');
    return {
      key: pair[0],
      value: pair[1] || ''
    };
  });
};

Tags.prototype.setValue = function(value) {
  value = value || [];
  if (value.asMutable) {
    value = value.asMutable({deep: true});
  }
  this.$el.tokenfield('setTokens', value.map((tag) => ({
    source: tag,
    value: getValue(tag)
  })));
};

export default Tags;
