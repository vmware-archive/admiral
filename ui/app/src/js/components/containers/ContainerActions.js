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

var createAction = function(actionName, iconName) {
  var actionLabel = i18n.t('app.container.actions.' + actionName);

  var html = '<div class="action">' +
             '<a href="#" class="btn admiral-btn admiral-btn-circle container-action-' +
      actionName + '">' +
               '<i class="fa fa-' + iconName + '"></i>' +
             '</a>' +
             '<div class="action-label">' + actionLabel + '</div>' +
           '</div>';

  return html;
};

const START_BUTTON = createAction('start', 'play');
const REBOOT_BUTTON = createAction('reboot', 'refresh');
const STOP_BUTTON = createAction('stop', 'stop');
const CLONE_BUTTON = createAction('clone', 'files-o');
const DETAILS_BUTTON = createAction('details', 'eye');
const REMOVE_BUTTON = createAction('remove', 'trash');

function ContainerActions() {
  this.$el = $('<div>', {class: 'container-actions'});

  this.$el.on('click', 'a', function(e) {
    e.preventDefault();
  });
}

ContainerActions.prototype.updateActions = function(container) {
  this.$el.empty();

  this.$el.append($.parseHTML(DETAILS_BUTTON));
  this.$el.append($.parseHTML(CLONE_BUTTON));

  if (container.powerState === constants.CONTAINERS.STATES.RUNNING ||
      container.powerState === constants.CONTAINERS.STATES.REBOOTING) {
    this.$el.append($.parseHTML(REBOOT_BUTTON));
    this.$el.append($.parseHTML(STOP_BUTTON));
  } else if (container.powerState === constants.CONTAINERS.STATES.STOPPED) {
    this.$el.append($.parseHTML(START_BUTTON));
  }

  this.$el.append($.parseHTML(REMOVE_BUTTON));
};

ContainerActions.prototype.getEl = function() {
  return this.$el;
};

export default ContainerActions;
