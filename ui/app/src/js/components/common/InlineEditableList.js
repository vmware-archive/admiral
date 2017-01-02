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

import constants from 'core/constants';
import utils from 'core/utils';
import InlineDeleteConfirmationTemplate from
  'components/common/InlineDeleteConfirmationTemplate.html';

function InlineEditableList($el, ListTemplate, RowRenderers) {
  this.$el = $el;

  $el.html(ListTemplate());
  this.RowRenderers = RowRenderers;
  var $table = $el.find('.inline-editable-list > table, .inline-editable-list > div > table');
  this.$tbody = $table.find('tbody');

  this.sort = new Tablesort($table[0]);

  addEventListeners.call(this);
}

InlineEditableList.prototype.setRowEditor = function(RowEditor) {
  this.rowEditor = new RowEditor();
};

InlineEditableList.prototype.setEditCallback = function(editCallback) {
  this.editCallback = editCallback;
};

InlineEditableList.prototype.setDeleteCallback = function(deleteCallback) {
  this.deleteCallback = deleteCallback;
};

InlineEditableList.prototype.setData = function(data) {
  if (this.data !== data) {
    var oldData = this.data || {};
    if (oldData.items !== data.items) {
      clearEdittedRow.call(this);
    }

    if (oldData.items !== data.items ||
        oldData.validationErrors !== data.validationErrors) {
      updateListElements.call(this, data);
    }

    if (oldData.editingItemData !== data.editingItemData) {
      updateeditingItemData.call(this, oldData.editingItemData, data.editingItemData);
    }

    this.data = data;
  }
};

var updateListElements = function(data) {
  if (data.items === constants.LOADING) {
    this.$el.find('.inline-editable-list > .loading').removeClass('hide');
  } else {
    this.$el.find('.inline-editable-list > .loading').addClass('hide');

    var $tbody = this.$tbody;
    $tbody.empty();

    var $totalItems = this.$el.find('.inline-editable-list .total-items');
    $totalItems.html('');

    var _this = this;

    if (data.items && data.items.length > 0) {
      data.items.forEach(function(entity) {
        var isNew = data.newItem === entity;
        var isUpdated = data.updatedItem === entity;
        var validationErrors = isUpdated ? data.validationErrors : null;
        var $row = createRow.call(_this, entity, isNew, isUpdated, validationErrors);
        if (isNew) {
          $tbody.prepend($row);
        } else {
          $tbody.append($row);
        }
      });

      $totalItems.html('(' + data.items.length + ')');

      this.sort.refresh();
    }

    // TODO: otherwise, no items, will need to show some message
  }
};

var addEventListeners = function() {
  var _this = this;

  this.$el.find('.inline-editable-list').on('click', '.new-item', function(e) {
    e.preventDefault();
    if (_this.editCallback) {
      _this.editCallback();
    }
  });

  this.$el.find('.inline-editable-list').on('click', '.item .item-edit', function(e) {
    e.preventDefault();

    var $item = $(e.currentTarget).closest('.item');
    var entity = $item.data('entity');
    if (_this.editCallback) {
      _this.editCallback(entity);
    }
  });

  this.$el.find('.inline-editable-list').on('click', '.item .item-delete', function(e) {
    e.preventDefault();

    removeConfirmationHoldersForParent(_this.$el);

    var $actions = $(e.currentTarget).closest('.table-actions');

    var $deleteConfirmationHolder = $(InlineDeleteConfirmationTemplate());
    var $deleteConfirmation = $deleteConfirmationHolder.find('.delete-inline-item-confirmation');

    // Fix for FF that does not support td with position relative
    $deleteConfirmationHolder.height($actions.outerHeight());
    $actions.children().hide();
    $actions.append($deleteConfirmationHolder);
    utils.slideToLeft($deleteConfirmation);
  });

  this.$el.find('.inline-editable-list')
    .on('click', '.item .delete-inline-item-confirmation-confirm', function(e) {
      e.preventDefault();

      var $item = $(e.currentTarget).closest('.item');
      var entity = $item.data('entity');
      if (_this.deleteCallback) {
        _this.deleteCallback(entity);
      }
    });

  this.$el.find('.inline-editable-list')
    .on('click', '.item .delete-inline-item-confirmation-cancel', function(e) {
      e.preventDefault();

      var $deleteConfirmationHolder = $(e.currentTarget)
        .closest('.delete-inline-item-confirmation-holder');
      removeConfirmationHolder($deleteConfirmationHolder);
    });
};

var updateeditingItemData = function(oldeditingItemData, editingItemData) {
  if (!editingItemData) {
    clearEdittedRow.call(this);
  } else {
    if (!oldeditingItemData || oldeditingItemData.item !== editingItemData.item) {
      clearEdittedRow.call(this);
      startEditItem.call(this, editingItemData.item);
    }

    this.rowEditor.setData(editingItemData);
  }
};

var clearEdittedRow = function() {
  if (this.$editItem) {
    this.$editItem.show();
    this.$editItem = null;
  }

  this.rowEditor.getEl().detach();
  this.$el.find('.new-item').removeAttr('disabled');
  this.$el.find('.inline-editable-list').removeClass('editing');
};

var startEditItem = function(itemData) {
  removeConfirmationHoldersForParent(this.$el);

  var $item = null;

  if (itemData && itemData.documentSelfLink) {
    this.$el.find('.inline-editable-list > table > tbody > .item, ' +
        '.inline-editable-list > div > table > tbody > .item').each(function() {
      var entity = $(this).data('entity');
      if (itemData.documentSelfLink === entity.documentSelfLink) {
        $item = $(this);
        return false;
      }
    });

    if (!$item) {
      throw new Error('Unknown item ' + itemData.documentSelfLink);
    }

    $item.after(this.rowEditor.getEl());
    $item.hide();
  } else {
    this.$tbody.prepend(this.rowEditor.getEl());
  }

  this.$editItem = $item;

  this.$el.find('.new-item').attr('disabled', 'true');

  this.$el.find('.inline-editable-list').addClass('editing');
};

var createRow = function(entity, isNew, isUpdated, validationErrors) {
  var $actualElement = this.RowRenderers.render(entity);

  $actualElement.data('entity', entity);

  if (isNew || isUpdated || validationErrors) {
    return highlightElement.call(this, entity, $actualElement, isNew, isUpdated, validationErrors);
  }

  return $actualElement;
};

var highlightElement = function(entity, $actualElement, isNew, isUpdated, validationErrors) {
  var $highlightElement = this.RowRenderers.renderHighlighted(entity, $actualElement, isNew,
                                                              isUpdated, validationErrors);

  setTimeout(function() {
    $highlightElement.replaceWith($actualElement);
  }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);

  return $highlightElement;
};

var removeConfirmationHoldersForParent = function($el) {
  var $deleteConfirmationHolder = $el.find('.delete-inline-item-confirmation-holder');
  removeConfirmationHolder($deleteConfirmationHolder);
};

var removeConfirmationHolder = function($deleteConfirmationHolder) {
  utils.fadeOut($deleteConfirmationHolder, function() {
    // Fix for FF that does not support td with position relative
    $deleteConfirmationHolder.parent().children().show();
    $deleteConfirmationHolder.remove();
  });
};

export default InlineEditableList;
