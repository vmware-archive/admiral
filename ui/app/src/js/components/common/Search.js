/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import constants from 'core/constants';
const SEPARATOR = ':';

function Search(properties, changeCallback) {
  var _this = this;
  this.$el = $('<div>', {
    class: 'query-search-input'
  });
  this.properties = properties;

  var optionsMatcher = function(q, cb) {
    var suggestions = [];
    if (q && properties.suggestionProperties) {
      var separatorIndex = q.indexOf(SEPARATOR);
      if (separatorIndex > -1) {
        if (properties.suggestionProperties.indexOf(q.substring(0, separatorIndex)) > -1) {
          // When going up and down through the suggestions, the text input changes to what the
          // desired value is. For example, when typed 'text' and there is suggestion for
          // 'name: text', when the user highlights it, the input will become 'name: text' and would
          // cause the new query to be 'name: text', however since this is something we know about,
          // We change it as expected to 'text'
          q = q.substring(separatorIndex + SEPARATOR.length);
        }
      }

      suggestions.push(q);
      for (let i = 0; i < properties.suggestionProperties.length; i++) {
        suggestions.push(properties.suggestionProperties[i] + SEPARATOR + ' ' + q);
      }
    }

    cb(suggestions);
  };

  this.$input = $('<input>', {
    type: 'text',
    class: 'form-control'
  });
  if (properties.placeholderHint) {
    this.$input.attr('placeholder', properties.placeholderHint);
  }

  var $controls = $('<div>', {
    class: 'query-search-input-controls form-control'
  });

  if (properties.occurrences) {
    createOccurrenceSelector.call(this, properties.occurrences);
    $controls.append(this.$occurrenceSelector);
  } else {
    var $icon = $('<span>', {
      class: 'fa fa-search form-control-feedback'
    });
    $controls.append($icon);
  }

  var $menu = $('<div>', {
    class: 'tt-menu'
  });

  this.$el.append($('<div>', {
      class: 'query-search-input-wrapper'
    })
    .append($controls).append(this.$input)).append($menu);

  function activate(e) {
    _this.$el.find('.tokenfield.form-control')[0].scrollLeft = e.target.offsetLeft - 100;
  }

  this.$input
    .on('tokenfield:createdtoken', (e) => {
      if (!this.eventsDisabled) {
        scrollToTypeahead.call(_this);
        changeCallback();
      }
      $(e.relatedTarget).on('click', activate);
    })
    .on('tokenfield:removedtoken', (e) => {
      if (!this.eventsDisabled) {
        changeCallback();
      }
      $(e.relatedTarget).off('click', activate);
    })
    .tokenfield({
      typeahead: [{
        menu: $menu
      }, {
        source: optionsMatcher,
        limit: 10
      }],
      minWidth: 300
    });

  this.$el.on('typeahead:beforeopen', (e) => {
    var ta = $(e.target).data('tt-typeahead');
    if (!ta.input.getQuery()) {
      e.preventDefault();
    }
  });

  this.$el.on('click', 'a.query-occurrence', (e) => {
    e.preventDefault();
    var oldValue = getQueryOccurrence.call(this);
    var newValue = $(e.target).data('occurrence').name;
    if (oldValue !== newValue) {
      setQueryOccurrence.call(this, newValue);
      changeCallback();
    }
  });
}

Search.prototype.getQueryOptions = function() {
  let tokens = this.$input.tokenfield('getTokens');
  let queryOptions = {};

  if (this.$occurrenceSelector) {
    queryOptions[constants.SEARCH_OCCURRENCE.PARAM] = getQueryOccurrence.call(this);
  }

  for (let i = 0; i < tokens.length; i++) {
    let token = tokens[i].value;
    let type = token.substring(0, token.indexOf(SEPARATOR));
    let value;

    var suggestionProperties = [];
    if (this.properties && this.properties.suggestionProperties) {
      suggestionProperties = this.properties.suggestionProperties;
    }
    if ((suggestionProperties.indexOf(type) > -1) ||
       token.substring(0, token.indexOf(SEPARATOR + ' '))) {
      value = token.substring(token.indexOf(SEPARATOR) + SEPARATOR.length).trim();
    } else {
      type = 'any';
      value = token;
    }

    if (queryOptions[type]) {
      queryOptions[type].push(value);
    } else {
      queryOptions[type] = [value];
    }
  }

  return queryOptions;
};

Search.prototype.setQueryOptions = function(queryOptions) {
  let newTokensStrings = [];
  if (queryOptions) {
    for (let key in queryOptions) {
      if (!queryOptions.hasOwnProperty(key)) {
        continue;
      }

      var queryOption = queryOptions[key];
      var currentOptions = [];
      if ($.isArray(queryOption)) {
        currentOptions = queryOption;
      } else if (queryOption) {
        currentOptions = [queryOption];
      }

      for (let i = 0; i < currentOptions.length; i++) {
        if (key === constants.SEARCH_CATEGORY_PARAM) {
          setQueryCategory.call(this, currentOptions[i]);
        } else if (key === constants.SEARCH_OCCURRENCE.PARAM) {
          setQueryOccurrence.call(this, currentOptions[i]);
        } else if (key === 'any') {
          newTokensStrings.push(currentOptions[i]);
        } else {
          newTokensStrings.push(key + ': ' + currentOptions[i]);
        }
      }
    }
  }

  let newTokens = [];

  let tokens = this.$input.tokenfield('getTokens');
  for (let i = 0; i < tokens.length; i++) {
    let token = tokens[i];
    if (newTokensStrings.indexOf(token.value) !== -1) {
      newTokens.push(token);
      newTokensStrings.splice(newTokensStrings.indexOf(token.value), 1);
    }
  }

  for (let i = 0; i < newTokensStrings.length; i++) {
    newTokens.push({
      value: newTokensStrings[i],
      label: newTokensStrings[i]
    });
  }

  this.eventsDisabled = true;
  this.$input.tokenfield('setTokens', newTokens);

  scrollToTypeahead.call(this);
  this.eventsDisabled = false;
};

Search.prototype.getEl = function() {
  return this.$el;
};

var scrollToTypeahead = function() {
  var typeaheadLeft = this.$el.find('.twitter-typeahead')[0].offsetLeft;
  var parentWidth = this.$el.find('.tokenfield.form-control')[0].offsetWidth;

  if (typeaheadLeft < 0 || typeaheadLeft > parentWidth) {
    this.$el.find('.tokenfield.form-control')[0].scrollLeft = typeaheadLeft;
  }
};

var createOccurrenceSelector = function(occurrences) {
  this.occurrences = {};
  var $occurrencesEl = $('<ul>', {
    class: 'dropdown-menu'
  });
  for (var i = 0; i < occurrences.length; i++) {
    var occurrence = occurrences[i];
    this.occurrences[occurrence.name] = occurrence;
    var $occurrenceEl = $('<a>', {
        href: '#',
        class: 'query-occurrence'
      })
      .data('occurrence', occurrence).text(occurrence.label);
    $occurrencesEl.append($('<li>').append($occurrenceEl));
  }

  this.$occurrenceSelector = $('<div>', {
      class: 'query-occurrence-dropdown dropdown'
    })
    .append($('<button>', {
        class: 'btn btn-outline dropdown-toggle',
        'data-toggle': 'dropdown',
        'aria-haspopup': 'true',
        'aria-expanded': 'false'
      })
      .append($('<span>', {
        class: 'fa fa-search form-control-feedback'
      }))
      .append($('<span>', {
        class: 'selected-query-occurrence'
      }))
      .append($('<span>', {
        class: 'caret'
      }))
    ).append($occurrencesEl);

  setQueryOccurrence.call(this, occurrences[0].name);
};

var getQueryOccurrence = function() {
  return this.$occurrenceSelector.find('.selected-query-occurrence').data('occurrence').name;
};

var setQueryOccurrence = function(occurrenceName) {
  if (this.occurrences) {
    var occurrence = this.occurrences[occurrenceName];
    if (!occurrence) {
      throw new Error('Unknown occurrence [' + occurrenceName + ']');
    }

    this.$occurrenceSelector.find('.selected-query-occurrence').text(occurrence.label)
      .data('occurrence', occurrence);
  }
};

var setQueryCategory = function(categoryName) {
  if (this.properties.placeholderHintByCategory) {
    var placeholderHint = this.properties.placeholderHintByCategory[categoryName];
    this.$input.attr('placeholder', placeholderHint);
    this.$el.find('.token-input.tt-input').attr('placeholder', placeholderHint);
  }
  if (this.properties.suggestionPropertiesByCategory) {
    this.properties.suggestionProperties =
      this.properties.suggestionPropertiesByCategory[categoryName];
  }
};

export default Search;
