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

const DropdownSearchMenuTemplate = require('./DropdownSearchMenuTemplate');
const $ = require('jquery');
const utils = require('../../core/formatUtils');
const ITEM_HIGHLIGHT_ACTIVE_TIMEOUT = 1500;

let _i18n;
let _hooksConfigured;

function DEFAULT_RENDERER(itemSpec) {
  var result = '';
  if (itemSpec.icon) {
    result += `<span class="fa fa-${itemSpec.icon}"></span>`;
  }
  if (itemSpec.iconSrc) {
    result += `<img src="${itemSpec.iconSrc}">`;
  }

  if (itemSpec.isBusy) {
    result += `<span class="spinner spinner-inline"></span>`;
  }

  result += `<span>${utils.escapeHtml(itemSpec.name)}</span>`;

  return result;
}

function createItem(itemSpec, renderer) {
  var anchor = $('<a>', {
    role: 'menuitem',
    href: '#',
    disabled: !!itemSpec.isBusy || !!itemSpec.isDisabled
  });
  anchor.attr('data-name', itemSpec.name);

  if (renderer) {
    anchor.html(renderer(itemSpec));
  } else {
    anchor.html(DEFAULT_RENDERER(itemSpec));
  }

  var item = $('<li>', {
    role: 'presentation',
    disabled: !!itemSpec.isBusy || !!itemSpec.isDisabled
  }).append(anchor);
  item.data('spec', itemSpec);

  return item;
}


function updateFilteredOptions($el, options, filter, selectedOption, optionsRenderer) {
  var $options = $el.find('.dropdown-options');
  $options.empty();

  var newElements = $();
  if (options) {
    for (var i = 0; i < options.length; i++) {
      var option = options[i];

      if (!filter || matchesFilter(option, filter)) {
        var newElement = createItem(option, optionsRenderer);
        if (selectedOption === option) {
          newElement.addClass('active');
        }

        newElements = newElements.add(newElement);
      }
    }
  }

  $options.append(newElements);
}

function applyExternallyFilteredOptions($el, result, filter, selectedOption, optionsRenderer) {
  var $options = $el.find('.dropdown-options');
  $options.empty();

  var options = result.items || [];
  if (options.length > 0) {
    var newElements = $();

    for (var i = 0; i < options.length; i++) {
      var option = options[i];
      var newElement = createItem(option, optionsRenderer);
      if (selectedOption === option) {
        newElement.addClass('active');
      }

      newElements = newElements.add(newElement);
    }

    $options.append(newElements);

    var i18nOption = {
      count: options.length,
      totalCount: result.totalCount
    };
    $options.append($('<div>').addClass('dropdown-options-hint')
      .text(_i18n.t('dropdownSearchMenu.showingCount', i18nOption)));
  } else {
    $options.append($('<div>').addClass('dropdown-options-hint')
      .text(_i18n.t('dropdownSearchMenu.noResults')));
  }
}

function matchesFilter(option, filter) {
  return option && option.name && option.name.toLowerCase().indexOf(filter) !== -1;
}

function invokeFilterCallback(filter) {
  var $search = this.$el.find('.search-input');

  clearTimeout(this.timeout);

  $search.addClass('loading');
  this.timeout = setTimeout(() => {
    this.filterCallback(filter, (result) => {
      if (this.filter !== filter) {
        // Filter is already different, no need to apply options
        return;
      }
      $search.removeClass('loading');
      applyExternallyFilteredOptions(this.$el, result, this.filter, this.selectedOption,
        this.optionsRenderer);
    });
  }, 300);
}

module.exports = class DropdownSearchMenu {
    constructor(el, componentOptions) {
      if (!_i18n) {
        _i18n = window.i18n;
      }
      let $el = el.length ? el : $(el);
      this.$el = $el;
      this.componentOptions = componentOptions;

      $el.html(DropdownSearchMenuTemplate(componentOptions));

      $el.find('input').click(function(e) {
        e.stopImmediatePropagation();
        e.preventDefault();
        return false;
      });

      if (!componentOptions.searchDisabled) {
        var searchInput = $el.find('.dropdown-search input');

        searchInput.bind('input change', (e) => {
          e.stopImmediatePropagation();
          e.preventDefault();

          this.setFilter(searchInput.val());
        });
      }

      let $dropdown = $el.find('.dropdown');

      $dropdown.on('click', '.dropdown-options li', (e) => {
        e.preventDefault();

        var $target = $(e.currentTarget);
        if ($target.attr('disabled')) {
          return;
        }

        var option = $target.data('spec');

        this.setSelectedOption(option);
      });

      $dropdown.on('click', '.dropdown-manage li', (e) => {
        e.preventDefault();

        var option = $(e.currentTarget).data('spec');
        this.setSelectedOption(null);

        let isClearSelection = option.id === '_clear';

        if (!isClearSelection) {
          if (option.action) {
            option.action();
          } else if (this.manageOptionSelectCallback) {
            this.manageOptionSelectCallback(option);
          }
        }

        if (isClearSelection && this.clearOptionSelectCallback) {
          this.clearOptionSelectCallback();
        }
      });

      $el.on('show.bs.dropdown', '.dropdown', () => {
        if (this.filterCallback) {
          invokeFilterCallback.call(this, this.filter);
        }
      });

      this.setSelectedOption(null);
      this.setManageOptions({});
    }

    /** Provides an async callback to provide options based on filter, iseful for server side search */
    setFilterCallback(callback) {
      this.filterCallback = callback;
    }

    /** Provides static options, useful for client side filtering */
    setOptions(options) {
      this.options = options;
      updateFilteredOptions(this.$el, this.options, this.filter, this.selectedOption,
        this.optionsRenderer);
    }

    setManageOptions(manageOptions) {
      var $dropDownManage = this.$el.find('.dropdown-manage');
      $dropDownManage.empty();

      var newElements = $();
      if (manageOptions) {
        for (var i = 0; i < manageOptions.length; i++) {
          var option = manageOptions[i];
          newElements = newElements.add(createItem(option));
        }
      }

      newElements = newElements.add(createItem({
          id: '_clear',
          name: _i18n.t('dropdownSearchMenu.clear'),
          icon: 'close'
      }));

      $dropDownManage.append(newElements);

      if (this.selectedOption === null) {
        var children = $dropDownManage.children();
        children.last().hide();
        $dropDownManage.toggleClass('hide', children.length === 1);
        this.$el.find('.divider').toggleClass('hide', children.length === 1);
      }
    }

    setFilter(filter) {
      if (filter) {
        filter = filter.toLowerCase();
      }

      this.filter = filter;

      if (this.filterCallback) {
        invokeFilterCallback.call(this, this.filter);
      } else {
        updateFilteredOptions(this.$el, this.options, this.filter, this.selectedOption);
      }
    };

    setLoading(isLoading) {
      if (this.isLoading !== isLoading) {
        if (isLoading) {
          this.$el.find('.dropdown-toggle').prop('disabled', true);
          this.$el.find('.loading').removeClass('hide');
        } else {
          this.$el.find('.dropdown-toggle').prop('disabled', !!this.disabled);
          this.$el.find('.loading').addClass('hide');
        }

        this.isLoading = isLoading;
      }
    }

    setDisabled(disabled) {
      this.disabled = disabled;
      this.$el.find('.dropdown-toggle').prop('disabled', disabled);
    }

    setSelectedOption(option) {
      if (this.selectedOption === option) {
        return;
      }

      this.selectedOption = option;

      var $dropDownManage = this.$el.find('.dropdown-manage');
      this.$el.find('.dropdown-options li.active').removeClass('active');

      if (option) {
        // value to select has been supplied
        if (this.valueRenderer) {
          this.$el.find('.dropdown-title').html(this.valueRenderer(option));
        } else {
          this.$el.find('.dropdown-title').text(option.name);
        }
        this.$el.find('.dropdown-title').removeClass('placeholder');
        this.$el.find('.dropdown-options li').each(function(n, optionEl) {
          if ($(optionEl).data('spec') === option) {
            $(optionEl).addClass('active');
          }
        });

        // Since this is not a typical input but a custom one, standard focus() and :focus don't work.
        this.$el.addClass('focus');

        setTimeout(() => {
          this.$el.removeClass('focus');
        }, ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);

        if (this.optionSelectCallback) {
          this.optionSelectCallback(option);
        }

        $dropDownManage.children().last().show();
        $dropDownManage.toggleClass('hide', false);
        this.$el.find('.divider').toggleClass('hide', false);
      } else {
        this.$el.find('.dropdown-title').text(this.componentOptions.title);
        if (!this.$el.find('.dropdown-title').hasClass('placeholder')) {
          this.$el.find('.dropdown-title').addClass('placeholder');
        }
        var children = $dropDownManage.children();
        children.last().hide();
        $dropDownManage.toggleClass('hide', children.length === 1);
        this.$el.find('.divider').toggleClass('hide', children.length === 1);
      }
    }

    getSelectedOption() {
      return this.selectedOption;
    }

    setOptionSelectCallback(optionSelectCallback) {
      this.optionSelectCallback = optionSelectCallback;
    }

    setManageOptionSelectCallback(manageOptionSelectCallback) {
      this.manageOptionSelectCallback = manageOptionSelectCallback;
    }

    /*
     * To be called on clearance of the selection.
     */
    setClearOptionSelectCallback(clearOptionSelectCallback) {
      this.clearOptionSelectCallback = clearOptionSelectCallback;
    }

    setOptionsRenderer(optionsRenderer) {
      this.optionsRenderer = optionsRenderer;
    }

    setValueRenderer(valueRenderer) {
      this.valueRenderer = valueRenderer;
    }

    static setI18n(i18n) {
      _i18n = i18n;
    }

    static configureCustomHooks() {
      if (_hooksConfigured) {
        return;
      }
      // Bootstrap-like hooks
      $(document).on('click', '.dropdown', (e) => {
        e.preventDefault();
        e.stopImmediatePropagation();

        var $dropdown = $(e.currentTarget);
        $dropdown.toggleClass('open');
      });

      $(document).on('click', () => {
        $('.dropdown').removeClass('open');
      });

      _hooksConfigured = true;
    }
}
