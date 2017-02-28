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

import { AppActions } from 'actions/Actions';
import constants from 'core/constants';
import utils from 'core/utils';
import services from 'core/services';
import docsHelp from 'components/common/DocsHelp';

var AppMixin = {
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  data: function() {
    return {
      constants: constants,
      sideClass: 'no-side'
    };
  },
  attached: function() {
    this.unwatchModel = this.$watch('model', (model) => {
      this.updateSelectedSideClass(model);
    }, {immediate: true});

    var $centerViewEl = $(this.$el).find('.center-view');
    $centerViewEl.on('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd',
      (e) => {
        if (e.target === $centerViewEl[0]) {
          unsetPostTransitionGridTargetWidth($centerViewEl);
        }
      }
    );

    AppActions.init();
  },
  detached: function() {
    this.unwatchModel();
  },
  methods: {
    updateSelectedSideClass: function(model) {
      var selectedClass = 'no-side';
      if (model && model.sideView && model.centerView) {
        if (centerViewHasExpandedContext(model.centerView)) {
          selectedClass = 'small-side';
        } else {
          selectedClass = 'normal-side';
        }
      } else if (model.hostsTransition) {
        selectedClass = 'normal-side';
      }

      if (this.sideClass !== selectedClass) {
        this.sideClass = selectedClass;

        if (this.$refs.centerView && this.$refs.centerView.requiresPreTransitionWidth) {
          Vue.nextTick(() => {
            var $appView = $(this.$el).find('.app-view');
            var $centerViewEl = $appView.find('.center-view');

            setPreTransitionGridTargetWidth($appView, $centerViewEl);
          });
        }
      }
    },
    logout: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      services.logout().then(() => {
        window.location.reload(true);
      }, (e) => {
        console.log(e);
      });
    },
    openHelp: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      docsHelp.open();
    },
    buildNumberSupported: function() {
      return !(utils.isApplicationEmbedded() || utils.isApplicationSingleView());
    }
  },
  computed: {
    buildNumber: function() {
      return utils.getBuildNumber();
    },
    currentUserShown: function() {
      return !utils.isApplicationEmbedded() && this.model.currentUser;
    },
    showResources: function() {
      return utils.showResourcesView(this.model.centerView.name);
    },
    showNgView: function() {
      return utils.isNgView(this.model.centerView.name);
    },
    viewRoute: function() {
      return this.model.centerView.data && this.model.centerView.data.viewRoute;
    }
  }
};

var centerViewHasExpandedContext = function(centerView) {
  if (centerView && centerView.data) {
    if (utils.hasExpandedContextView(centerView.data.hostAddView)) {
      return true;
    }

    if (utils.hasExpandedContextView(centerView.data.editingItemData)) {
      return true;
    }

    if (utils.hasExpandedContextView(centerView.data.taskAddView)) {
      return true;
    }

    if (utils.hasExpandedContextView(centerView.data.registries)) {
      return true;
    }

    if (utils.hasExpandedContextView(centerView.data)) {
      return true;
    }

    if (centerView.data.selectedItem) {
      return true;
    }
  }

  return false;
};

var unsetWidthTimeout;

var setPreTransitionGridTargetWidth = function($parent, $targetEl) {
  var $targetElClode = $('<' + $targetEl.prop('tagName') + '>',
                         {class: $targetEl.attr('class') + ' notransition'});

  $parent.append($targetElClode);

  $targetEl.css('width', $targetElClode.width());

  $targetElClode.remove();

  clearTimeout(unsetWidthTimeout);
  unsetWidthTimeout = setTimeout(() => {
    var width = $targetEl[0].style.width;
    if (width) {
      console.warn('transitionend did not cause width to be unset.');
      unsetPostTransitionGridTargetWidth($targetEl);
    }
  }, 500);
};

var unsetPostTransitionGridTargetWidth = function($targetEl) {
  $targetEl.css('width', '');
};

export default AppMixin;
