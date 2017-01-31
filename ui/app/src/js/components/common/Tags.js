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
  this.$el
    .on('tokenfield:createdtoken tokenfield:editedtoken tokenfield:removedtoken', () => {
      if (this.changeCallback) {
        this.changeCallback(this.getValue());
      }
    })
    .tokenfield({
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
          suggestions.sort((a, b) => a.value === b.value ? 0 : +(a.value > b.value) || -1);
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
  return this.$el.tokenfield('getTokens').reduce((prev, curr) => {
    let pair = curr.value.split(':');
    let item = {
      key: pair[0],
      value: pair[1] || ''
    };
    if (prev.find((tag) => tag.key === item.key && tag.value === item.value)) {
      return prev;
    }
    return [...prev, item];
  }, []);
};

Tags.prototype.setValue = function(value) {
  value = value || [];
  let tokens = [];
  value.forEach((tag) => {
    tokens.push({
      source: $.extend({}, tag),
      value: getValue(tag)
    });
  });
  this.$el.tokenfield('setTokens', [...tokens]);
};

Tags.prototype.setChangeCallback = function(changeCallback) {
  this.changeCallback = changeCallback;
};

export default Tags;
