import CustomDropdownSearchMenuTemplate from
 'components/common/CustomDropdownSearchMenuTemplate.html';
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

function CustomDropdownSearchMenu($el, componentOptions) {
  var _this = this;

  this.$el = $el;
  this.componentOptions = componentOptions;

  $el.html(CustomDropdownSearchMenuTemplate(componentOptions));

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

/** Provides an async callback to provide options based on filter, iseful for server side search */
CustomDropdownSearchMenu.prototype.setFilterCallback = function(callback) {
  this.filterCallback = callback;
};

/** Provides static options, useful for client side filtering */
CustomDropdownSearchMenu.prototype.setOptions = function(options) {
  this.options = options;
  updateFilteredOptions(this.$el, this.options, this.filter, this.selectedOption);
};

CustomDropdownSearchMenu.prototype.setManageOptions = function(manageOptions) {
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

  $dropDownManage.append(newElements);

  if (this.selectedOption === null) {
    var children = $dropDownManage.children();
    children.last().hide();
    $dropDownManage.toggleClass('hide', children.length === 1);
    this.$el.find('.divider').toggleClass('hide', children.length === 1);
  }
};

CustomDropdownSearchMenu.prototype.setFilter = function(filter) {
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

CustomDropdownSearchMenu.prototype.setLoading = function(isLoading) {
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

CustomDropdownSearchMenu.prototype.setDisabled = function(disabled) {
  this.disabled = disabled;
  this.$el.find('.dropdown-toggle').prop('disabled', disabled);
};

CustomDropdownSearchMenu.prototype.setSelectedOption = function(option) {
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
    $dropDownManage.toggleClass('hide', false);
    this.$el.find('.divider').toggleClass('hide', false);
  } else {
    this.$el.find('.dropdown-title').html(this.componentOptions.title);
    if (!this.$el.find('.dropdown-title').hasClass('placeholder')) {
      this.$el.find('.dropdown-title').addClass('placeholder');
    }
    var children = $dropDownManage.children();
    children.last().hide();
    $dropDownManage.toggleClass('hide', children.length === 1);
    this.$el.find('.divider').toggleClass('hide', children.length === 1);
  }
};

CustomDropdownSearchMenu.prototype.getSelectedOption = function() {
  return this.selectedOption;
};

CustomDropdownSearchMenu.prototype.setOptionSelectCallback = function(optionSelectCallback) {
  this.optionSelectCallback = optionSelectCallback;
};

CustomDropdownSearchMenu.prototype.setManageOptionSelectCallback =
  function(manageOptionSelectCallback) {
    this.manageOptionSelectCallback = manageOptionSelectCallback;
  };

/*
 * To be called on clearance of the selection.
 */
CustomDropdownSearchMenu.prototype.setClearOptionSelectCallback = function(
  clearOptionSelectCallback) {
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

function applyExternallyFilteredOptions($el, options, filter, selectedOption) {
  $el.find('.dropdown-options').empty();

  if (options && options.length > 0) {
    var newElements = $();

    for (var i = 0; i < options.length; i++) {
      var option = options[i];
      var newElement = createItem(option);
      if (selectedOption === option) {
        newElement.addClass('active');
      }

      newElements = newElements.add(newElement);
    }

    $el.find('.dropdown-options').append(newElements);
  } else if (filter) {
    $el.find('.dropdown-options').append($('<div>').addClass('dropdown-options-hint')
      .text(i18n.t('dropdownSearchMenu.noResults')));
  }
}

function matchesFilter(option, filter) {
  return option && option.name && option.name.toLowerCase().indexOf(filter) !== -1;
}

function invokeFilterCallback(filter) {
  var $search = this.$el.find('.search-input');

  clearTimeout(this.timeout);
  if (!filter) {
    applyExternallyFilteredOptions(this.$el, [], this.filter, this.selectedOption);
    $search.removeClass('loading');
    return;
  }

  $search.addClass('loading');
  this.timeout = setTimeout(() => {
    this.filterCallback(filter, (options) => {
      if (this.filter !== filter) {
        // Filter is already different, no need to apply options
        return;
      }
      $search.removeClass('loading');
      applyExternallyFilteredOptions(this.$el, options, this.filter, this.selectedOption);
    });
  }, 300);
}

export default CustomDropdownSearchMenu;
