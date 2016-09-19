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

function createHeaders(model) {
  var $headers = $('<ul>');

  for (var key in model) {
    if (model.hasOwnProperty(key)) {
      var $header = $('<div>', {
        class: 'multicolumn-header'
      }).append($('<div>', {class: 'multicolumn-header-label'}).html(model[key].header));

      $headers.append($('<li>').append($header));
    }
  }

  return $headers;
}

function createItem(object, model) {
  var $multicolumnInput = $('<div>', {
    class: 'multicolumn-input'
  });

  var $inputs = $('<ul>');

  for (var key in model) {
    if (model.hasOwnProperty(key)) {
      var type = model[key].type;
      var $input;

      if (type === 'dropdown') {
        $input = $('<select>', {
          class: 'inline-input',
          name: key
        });
        $input.append($('<option>'));
        if (model[key].options) {
          model[key].options.forEach(function(option) {
            $input.append($('<option>').attr('value', option.value).html(option.label));
          });
        }

        if (object && object[key]) {
          $input.val(object[key]);
        }
      } else {
        $input = $('<input>', {
          class: 'inline-input',
          name: key,
          placeholder: model[key].placeholder
        });
        if (object && object[key]) {
          $input.val(object[key]);
        }
        if (type) {
          $input.attr('type', type);
        }
      }

      if (type === 'checkbox') {
        if (object && object[key]) {
          $input.attr('checked', object[key]);
        }
        var $wrapper = $('<div>').append($input);
        $input = $('<label>', {
          class: 'checkbox-placehodler'
        }).html($wrapper.html() + model[key].placeholder);
      } else {
        $input.addClass('form-control');
      }

      var $inputHolder = $('<div>', {
        class: 'inline-input-holder',
        name: key
      });

      $inputHolder.append($input).append($('<span>', {class: 'help-block'}));

      $inputs.append($('<li>').append($inputHolder));
    }
  }

  var $removeButton = $('<a>', {
      href: '#',
      tabindex: '-1',
      class: 'multicolumn-input-remove'
    })
    .append($('<i>', {
      class: 'btn admiral-btn admiral-btn-circle fa fa-minus'
    }));

  var $addButton = $('<a>', {
      href: '#',
      class: 'multicolumn-input-add'
    })
    .append($('<i>', {
      class: 'btn admiral-btn admiral-btn-circle fa fa-plus'
    }));

  $multicolumnInput.append($('<div>', {
    class: 'multicolumn-input-controls'
  }).append($inputs));
  $multicolumnInput.append($('<div>', {
      class: 'multicolumn-input-toolbar'
    }).append($removeButton)
    .append($addButton));

  return $multicolumnInput;
}

function MulticolumnInputs($el, model) {
  var _this = this;

  this.$el = $el;
  this.$el.addClass('multicolumn-inputs');
  this.model = model;
  this.$hiddenProperties = [];

  this.$list = $('<div>', {
    class: 'multicolumn-inputs-list'
  });
  this.$el.append(this.$list);

  var headers = false;
  for (var key in model) {
    if (model.hasOwnProperty(key)) {
      headers = model[key].header || headers;
    }
  }

  if (headers) {
    this.$listHead = $('<div>', {
      class: 'multicolumn-inputs-list-head'
    });
    this.$list.append(this.$listHead);
    this.$listHead.append(createHeaders(model));
  }

  this.$listBodyWrapper = $('<div>', {
    class: 'multicolumn-inputs-list-body-wrapper'
  });
  this.$list.append(this.$listBodyWrapper);

  this.$listBody = $('<div>', {
    class: 'multicolumn-inputs-list-body'
  });
  this.$listBodyWrapper.append(this.$listBody);
  insertEmptyProperty(this.$listBody, model);

  this.$list.on('click', '.multicolumn-input-remove', function(e) {
    e.preventDefault();
    var $propetyElement = $(e.currentTarget).closest('.multicolumn-input');
    removeProperty($propetyElement, _this.$listBody, model, false, this._keepRemovedProperties);
  });

  this.$listBody.on('click', '.multicolumn-input-add', function(e) {
    e.preventDefault();
    insertEmptyProperty(_this.$listBody, model, true);
  });

  this.$listBody.on('keypress', '.multicolumn-input', function(e) {
    if (e.which === 13) {
      var item = $(e.target).parents('.multicolumn-input');
      if (item.parent().children().size() === item.index() + 1) {
        insertEmptyProperty(_this.$listBody, model, true);
      } else {
        item.next().find('input').first().focus();
      }
    }
  });

  this._keepRemovedProperties = true;
}

MulticolumnInputs.prototype.setOptions = function(keysToOptions) {
  for (var key in keysToOptions) {
    if (keysToOptions.hasOwnProperty(key)) {
      this.model[key].options = keysToOptions[key];
    }
  }
  this.setData(this._data);
};

MulticolumnInputs.prototype.keepRemovedProperties = function(value) {
  this._keepRemovedProperties = value;
};

MulticolumnInputs.prototype.setData = function(data) {
  this.$listBody.empty();
  this.$hiddenProperties = []; // clear hidden props

  if (data && data.length > 0) {
    for (var i = 0; i < data.length; i++) {
      if (this.$shouldHide && this.$shouldHide(data[i])) {
        this.$hiddenProperties.push(data[i]);
      } else {
        var $item = createItem(data[i], this.model);
        this.$listBody.append($item);
      }
    }
  }

  if (this.$listBody.children().length === 0) {
    insertEmptyProperty(this.$listBody, this.model);
  }
  this._data = data;
};

MulticolumnInputs.prototype.getData = function() {
  var $items = this.$listBody.children();
  var data = [];

  for (var i = 0; i < $items.length; i++) {
    var $item = $($items[i]);

    var hasNonEmptyValue = false;
    var noMultipleValuesWithNull = true;
    var object = {};
    for (var key in this.model) {
      if (this.model.hasOwnProperty(key)) {
        var val;
        var $input = $item.find('.inline-input[name="' + key + '"]');
        if (this.model[key].type === 'checkbox') {
          val = $input.is(':checked');
        } else {
          val = $input.val();
          if (val === '__null' && 'name' in object) {
            var repetedValNull = $.grep($items, function(e) {
              return $(e).find('input').eq(0).val() === object.name;
              });
            if (repetedValNull.length > 1) {
              noMultipleValuesWithNull = false;
            }
            val = null;
          }
        }
        object[key] = val;
        if (!hasNonEmptyValue && val) {
          hasNonEmptyValue = true;
        }
      }
    }

    // Include only entries with actual values
    if (hasNonEmptyValue && noMultipleValuesWithNull) {
      data.push(object);
    }
  }

  if (this.$hiddenProperties.length > 0) {
    data = data.concat(this.$hiddenProperties);
  }

  return data;
};

MulticolumnInputs.prototype.setVisibilityFilter = function(shouldHide) {
  this.$shouldHide = shouldHide;
};

MulticolumnInputs.prototype.removeEmptyProperties = function() {
  var $items = this.$listBody.children();
  for (var i = 0; i < $items.length; i++) {
    var $item = $($items[i]);

    var hasNonEmptyValue = false;
    for (var key in this.model) {
      if (this.model.hasOwnProperty(key)) {
        var val = $item.find('.inline-input[name="' + key + '"]').val();
        var isCheckbox = this.model[key].type === 'checkbox';
        if (!isCheckbox && !hasNonEmptyValue && val) {
          hasNonEmptyValue = true;
        }
      }
    }

    // Remove entries with actual values
    if (!hasNonEmptyValue) {
      removeProperty($item, this.$listBody, this.model, true, this._keepRemovedProperties);
    }
  }
};

var insertEmptyProperty = function($listBody, model, focus) {
  var $emptyPropertyElement = createItem(null, model);
  let props = $listBody.children();
 for (var i = props.length - 1; i >= 0; i--) {
      if (props.eq(i).is((':visible'))) {
        props.find('.multicolumn-input-add').eq(i)
          .css('visibility', 'hidden');
        break;
      }
    }
  $listBody.append($emptyPropertyElement);
  if (focus) {
    $emptyPropertyElement.find('input').first().focus();
  }
};

var removeProperty = function($propertyElement, $listBody, $model,
                               isFromRemoveEmptyProperties, keepRemovedProperties) {
  let visibleProps = $listBody.children().filter(':visible');
  if (visibleProps.length >= 1) {
    //removing element which contains only value
    if (!keepRemovedProperties || $propertyElement.find('li').length === 1) {
      $propertyElement.remove();
    } else if ($propertyElement.find('input').eq(0).val() === '') {
      $propertyElement.remove();
    } else if ($propertyElement.find('input').last().attr('type') === 'checkbox') {
        $propertyElement.find('input').eq(1).prop('type', 'text').val('__null');
        $propertyElement.hide();
    } else {
      $propertyElement.find('input').last().prop('type', 'text').val('__null');
      $propertyElement.find('select').eq(1).val('__null');
      $propertyElement.hide();
    }
    if (visibleProps.length === 1 && isFromRemoveEmptyProperties !== true) {
        insertEmptyProperty($listBody, $model);
    }

    for (var i = visibleProps.length - 1; i >= 0; i--) {
      if (visibleProps.eq(i).is((':visible'))) {
        visibleProps.find('.multicolumn-input-add').eq(i)
          .css('visibility', 'visible');
        break;
      }
    }
  }

  let props = $listBody.children();
  if (props <= 1) {
    $propertyElement.find('input').first().val('');
    $propertyElement.find('select').first().val('');
    $propertyElement.find('input').last().val('');
    $propertyElement.find('select').last().val('');
  }
};

export default MulticolumnInputs;
