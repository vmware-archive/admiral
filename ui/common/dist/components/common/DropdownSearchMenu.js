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
var DropdownSearchMenuTemplate = require('./DropdownSearchMenuTemplate');
var $ = require('jquery');
var utils = require('../../core/formatUtils');
var ITEM_HIGHLIGHT_ACTIVE_TIMEOUT = 1500;
var _i18n;
var _hooksConfigured;
function DEFAULT_RENDERER(itemSpec) {
    var result = '';
    if (itemSpec.icon) {
        result += "<span class=\"fa fa-" + itemSpec.icon + "\"></span>";
    }
    if (itemSpec.iconSrc) {
        result += "<img src=\"" + itemSpec.iconSrc + "\">";
    }
    if (itemSpec.isBusy) {
        result += "<span class=\"spinner spinner-inline\"></span>";
    }
    result += "<span>" + utils.escapeHtml(itemSpec.name) + "</span>";
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
    }
    else {
        anchor.html(DEFAULT_RENDERER(itemSpec));
    }
    var item = $('<li>', {
        role: 'presentation',
        disabled: !!itemSpec.isBusy || !!itemSpec.isDisabled,
        style: 'display: block;'
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
    }
    else {
        $options.append($('<div>').addClass('dropdown-options-hint')
            .text(_i18n.t('dropdownSearchMenu.noResults')));
    }
}
function matchesFilter(option, filter) {
    return option && option.name && option.name.toLowerCase().indexOf(filter) !== -1;
}
function invokeFilterCallback(filter) {
    var _this = this;
    var $search = this.$el.find('.search-input');
    clearTimeout(this.timeout);
    $search.addClass('loading');
    this.timeout = setTimeout(function () {
        _this.filterCallback(filter, function (result) {
            if (_this.filter !== filter) {
                // Filter is already different, no need to apply options
                return;
            }
            $search.removeClass('loading');
            applyExternallyFilteredOptions(_this.$el, result, _this.filter, _this.selectedOption, _this.optionsRenderer);
        });
    }, 300);
}
module.exports = (function () {
    function DropdownSearchMenu(el, componentOptions) {
        var _this = this;
        if (!_i18n) {
            _i18n = window.i18n;
        }
        var $el = el.length ? el : $(el);
        this.$el = $el;
        this.componentOptions = componentOptions;
        $el.html(DropdownSearchMenuTemplate(componentOptions));
        $el.find('input').click(function (e) {
            e.stopImmediatePropagation();
            e.preventDefault();
            return false;
        });
        if (!componentOptions.searchDisabled) {
            var searchInput = $el.find('.dropdown-search input');
            searchInput.bind('input change', function (e) {
                e.stopImmediatePropagation();
                e.preventDefault();
                _this.setFilter(searchInput.val());
            });
        }
        var $dropdown = $el.find('.dropdown');
        $dropdown.on('click', '.dropdown-options li', function (e) {
            e.preventDefault();
            var $target = $(e.currentTarget);
            if ($target.attr('disabled')) {
                return;
            }
            var option = $target.data('spec');
            _this.setSelectedOption(option);
        });
        $dropdown.on('click', '.dropdown-manage li', function (e) {
            e.preventDefault();
            var option = $(e.currentTarget).data('spec');
            _this.setSelectedOption(null);
            var isClearSelection = option.id === '_clear';
            if (!isClearSelection) {
                if (option.action) {
                    option.action();
                }
                else if (_this.manageOptionSelectCallback) {
                    _this.manageOptionSelectCallback(option);
                }
            }
            if (isClearSelection && _this.clearOptionSelectCallback) {
                _this.clearOptionSelectCallback();
            }
        });
        $el.on('show.bs.dropdown', '.dropdown', function () {
            if (_this.filterCallback) {
                invokeFilterCallback.call(_this, _this.filter);
            }
        });
        this.setSelectedOption(null);
        this.setManageOptions({});
    }
    /*
     * Provides an async callback to provide options based on filter,
     * useful for server side search.
     */
    DropdownSearchMenu.prototype.setFilterCallback = function (callback) {
        this.filterCallback = callback;
    };
    /** Provides static options, useful for client side filtering */
    DropdownSearchMenu.prototype.setOptions = function (options) {
        this.options = options;
        updateFilteredOptions(this.$el, this.options, this.filter, this.selectedOption, this.optionsRenderer);
    };
    DropdownSearchMenu.prototype.setManageOptions = function (manageOptions) {
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
    };
    DropdownSearchMenu.prototype.setFilter = function (filter) {
        if (filter) {
            filter = filter.toLowerCase();
        }
        this.filter = filter;
        if (this.filterCallback) {
            invokeFilterCallback.call(this, this.filter);
        }
        else {
            updateFilteredOptions(this.$el, this.options, this.filter, this.selectedOption);
        }
    };
    ;
    DropdownSearchMenu.prototype.setLoading = function (isLoading) {
        if (this.isLoading !== isLoading) {
            if (isLoading) {
                this.$el.find('.dropdown-toggle').prop('disabled', true);
                this.$el.find('.loading').removeClass('hide');
            }
            else {
                this.$el.find('.dropdown-toggle').prop('disabled', !!this.disabled);
                this.$el.find('.loading').addClass('hide');
            }
            this.isLoading = isLoading;
        }
    };
    DropdownSearchMenu.prototype.setDisabled = function (disabled) {
        this.disabled = disabled;
        this.$el.find('.dropdown-toggle').prop('disabled', disabled);
    };
    DropdownSearchMenu.prototype.setSelectedOption = function (option) {
        var _this = this;
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
            }
            else {
                this.$el.find('.dropdown-title').text(option.name);
            }
            this.$el.find('.dropdown-title').removeClass('placeholder');
            this.$el.find('.dropdown-options li').each(function (n, optionEl) {
                if ($(optionEl).data('spec') === option) {
                    $(optionEl).addClass('active');
                }
            });
            // Since this is not a typical input but a custom one, standard focus()
            // and :focus don't work.
            this.$el.addClass('focus');
            setTimeout(function () {
                _this.$el.removeClass('focus');
            }, ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
            if (this.optionSelectCallback) {
                this.optionSelectCallback(option);
            }
            $dropDownManage.children().last().show();
            $dropDownManage.toggleClass('hide', false);
            this.$el.find('.divider').toggleClass('hide', false);
        }
        else {
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
    DropdownSearchMenu.prototype.getSelectedOption = function () {
        return this.selectedOption;
    };
    DropdownSearchMenu.prototype.setOptionSelectCallback = function (optionSelectCallback) {
        this.optionSelectCallback = optionSelectCallback;
    };
    DropdownSearchMenu.prototype.setManageOptionSelectCallback = function (manageOptionSelectCallback) {
        this.manageOptionSelectCallback = manageOptionSelectCallback;
    };
    /*
     * To be called on clearance of the selection.
     */
    DropdownSearchMenu.prototype.setClearOptionSelectCallback = function (clearOptionSelectCallback) {
        this.clearOptionSelectCallback = clearOptionSelectCallback;
    };
    DropdownSearchMenu.prototype.setOptionsRenderer = function (optionsRenderer) {
        this.optionsRenderer = optionsRenderer;
    };
    DropdownSearchMenu.prototype.setValueRenderer = function (valueRenderer) {
        this.valueRenderer = valueRenderer;
    };
    DropdownSearchMenu.setI18n = function (i18n) {
        _i18n = i18n;
    };
    DropdownSearchMenu.configureCustomHooks = function () {
        if (_hooksConfigured) {
            return;
        }
        // Bootstrap-like hooks
        $(document).on('click', '.dropdown-search-menu', function (e) {
            e.preventDefault();
            e.stopImmediatePropagation();
            var $dropdown = $(e.currentTarget);
            $dropdown.toggleClass('open');
        });
        $(document).on('click', function () {
            $('.dropdown-search-menu').removeClass('open');
        });
        _hooksConfigured = true;
    };
    return DropdownSearchMenu;
}());
//# sourceMappingURL=DropdownSearchMenu.js.map