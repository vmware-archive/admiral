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

import DropdownSearchMenuTemplate from 'DropdownSearchMenuTemplate';
import constants from 'core/constants';

function createItem(itemSpec) {
  var anchor = $('<a>', {
    role: 'menuitem',
    href: '#'
  });
  anchor.attr('data-name', itemSpec.name);
  if (itemSpec.icon) {
    anchor.append($('<span>', {
      class: 'fa fa-' + itemSpec.icon
    }));
  }
  if (itemSpec.iconSrc) {
    anchor.append($('<img >', {
      src: itemSpec.iconSrc
    }));
  }

  anchor.append($('<span>').html(itemSpec.name));

  var item = $('<li>', {
    role: 'presentation'
  }).append(anchor);
  item.data('spec', itemSpec);

  return item;
}

function DropdownSearchMenu($el, componentOptions) {
  var _this = this;

  this.$el = $el;
  this.componentOptions = componentOptions;

  $el.html(DropdownSearchMenuTemplate(componentOptions));

  $el.find('input').click(function(event) {
    event.preventDefault();
    return false;
  });

  if (!componentOptions.searchDisabled) {
    var searchInput = $el.find('.dropdown-search input');

    searchInput.bind('input change', function() {
      _this.setFilter(searchInput.val());
    });
  }

  $el.find('.dropdown').on('click', '.dropdown-options li', function(e) {
    e.preventDefault();

    var option = $(e.currentTarget).data('spec');
    _this.setSelectedOption(option);

    if (_this.optionSelectCallback) {
      _this.optionSelectCallback(option);
    }
  });

  $el.find('.dropdown').on('click', '.dropdown-manage li', function(e) {
    e.preventDefault();

    var option = $(e.currentTarget).data('spec');
    _this.setSelectedOption(null);

    let isClearSelection = option.id === '_clear';

    if (_this.manageOptionSelectCallback && !isClearSelection) {
      _this.manageOptionSelectCallback(option);
    }

    if (isClearSelection && _this.clearOptionSelectCallback) {
      _this.clearOptionSelectCallback();
    }
  });

  this.setSelectedOption(null);
  this.setManageOptions({});
}

DropdownSearchMenu.prototype.setOptions = function(options) {
  this.options = options;
  updateFilteredOptions(this.$el, this.options, this.filter, this.selectedOption);
};

DropdownSearchMenu.prototype.setManageOptions = function(manageOptions) {
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
      name: i18n.t('dropdownSearchMenu.clear'),
      icon: 'close'
  }));

  this.$el.find('.divider').toggleClass('hide', newElements.length === 0);
  $dropDownManage.toggleClass('hide', newElements.length === 0);
  $dropDownManage.append(newElements);

  if (this.selectedOption === null) {
    $dropDownManage.children().last().hide();
  }
};

DropdownSearchMenu.prototype.setFilter = function(filter) {
  if (filter) {
    filter = filter.toLowerCase();
  }

  this.filter = filter;

  updateFilteredOptions(this.$el, this.options, this.filter, this.selectedOption);
};

DropdownSearchMenu.prototype.setLoading = function(isLoading) {
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
};

DropdownSearchMenu.prototype.setDisabled = function(disabled) {
  this.disabled = disabled;
  this.$el.find('.dropdown-toggle').prop('disabled', disabled);
};

DropdownSearchMenu.prototype.setSelectedOption = function(option) {
  if (this.selectedOption === option) {
    return;
  }

  this.selectedOption = option;

  var $dropDownManage = this.$el.find('.dropdown-manage');
  this.$el.find('.dropdown-options li.active').removeClass('active');

  if (option) {
    // value to select has been supplied
    this.$el.find('.dropdown-title').html(option.name);
    this.$el.find('.dropdown-title').removeClass('placeholder');
    this.$el.find('.dropdown-options li').each(function(n, optionEl) {
      if ($(optionEl).data('spec') === option) {
        $(optionEl).addClass('active');
      }
    });

    // Since this is not a typical input but a custom one, standard focus() and :focus don't work.
    var _this = this;
    this.$el.addClass('focus');

    setTimeout(function() {
      _this.$el.removeClass('focus');
    }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);

    if (_this.optionSelectCallback) {
      _this.optionSelectCallback(option);
    }

    $dropDownManage.children().last().show();
  } else {
    this.$el.find('.dropdown-title').html(this.componentOptions.title);
    if (!this.$el.find('.dropdown-title').hasClass('placeholder')) {
      this.$el.find('.dropdown-title').addClass('placeholder');
    }
    $dropDownManage.children().last().hide();
  }
};

DropdownSearchMenu.prototype.getSelectedOption = function() {
  return this.selectedOption;
};

DropdownSearchMenu.prototype.setOptionSelectCallback = function(optionSelectCallback) {
  this.optionSelectCallback = optionSelectCallback;
};

DropdownSearchMenu.prototype.setManageOptionSelectCallback = function(manageOptionSelectCallback) {
  this.manageOptionSelectCallback = manageOptionSelectCallback;
};

/*
 * To be called on clearance of the selection.
 */
DropdownSearchMenu.prototype.setClearOptionSelectCallback = function(clearOptionSelectCallback) {
  this.clearOptionSelectCallback = clearOptionSelectCallback;
};

function updateFilteredOptions($el, options, filter, selectedOption) {
  $el.find('.dropdown-options').empty();

  var newElements = $();
  if (options) {
    for (var i = 0; i < options.length; i++) {
      var option = options[i];

      if (!filter || matchesFilter(option, filter)) {
        var newElement = createItem(option);
        if (selectedOption === option) {
          newElement.addClass('active');
        }

        newElements = newElements.add(newElement);
      }
    }
  }

  $el.find('.dropdown-options').append(newElements);
}

function matchesFilter(option, filter) {
  return option && option.name && option.name.toLowerCase().indexOf(filter) !== -1;
}

export default DropdownSearchMenu;
