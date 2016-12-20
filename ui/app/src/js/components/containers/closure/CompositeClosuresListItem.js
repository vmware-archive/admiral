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

import CompositeClosuresListItemVue from
  'components/containers/closure/CompositeClosuresListItemVue.html';
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin'; //eslint-disable-line
import VueDeleteItemConfirmation from 'components/common/VueDeleteItemConfirmation'; //eslint-disable-line
import utils from 'core/utils';
import { NavigationActions } from 'actions/Actions'; //eslint-disable-line

var CompositeClosuresListItem = Vue.extend({
  template: CompositeClosuresListItemVue,
  mixins: [DeleteConfirmationSupportMixin],
  props: {
    model: {required: true}
  },
  computed: {
    numberOfIcons: function() {
      return Math.min(this.model.icons && this.model.icons.length, 4);
    },
    closureIcon: function() {
      return utils.getClosureIcon(this.model.runtime);
    }
  },
  methods: {
    containerStatusDisplay: utils.containerStatusDisplay,

    openClosure: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      let closureDescriptionId = utils.getDocumentId(this.model.documentSelfLink);

      NavigationActions.openCompositeClosureDetails(closureDescriptionId);
    },

    operationSupported: function(op) {
      return utils.operationSupported(op, this.model);
    }

  }
});

Vue.component('composite-closure-grid-item', CompositeClosuresListItem);

export default CompositeClosuresListItem;
