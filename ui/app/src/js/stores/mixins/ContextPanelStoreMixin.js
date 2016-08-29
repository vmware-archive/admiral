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

import utils from 'core/utils';

var ContextPanelStoreMixin = {
  isContextPanelActive: function(name) {
    var activeItem = utils.getIn(this.data, ['contextView', 'activeItem']);
    return activeItem && activeItem.name === name;
  },

  openToolbarItem: function(name, data, shouldSelectAndComplete) {
    this.setInData(['contextView', 'expanded'], true);
    this.setInData(['contextView', 'activeItem'], {
      name: name,
      data: data
    });
    this.setInData(['contextView', 'shouldSelectAndComplete'], shouldSelectAndComplete);
    this.emitChange();
  },

  setActiveItemData: function(itemData) {
    this.setInData(['contextView', 'activeItem', 'data'], itemData);
    this.emitChange();
  },

  closeToolbar: function() {
    this.setInData(['contextView', 'expanded'], false);
    this.setInData(['contextView', 'activeItem'], null);
    this.emitChange();
  }
};

export default ContextPanelStoreMixin;
