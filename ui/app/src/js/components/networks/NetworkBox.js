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

import InlineDeleteConfirmationTemplate from
  'components/common/InlineDeleteConfirmationTemplate.html';
import utils from 'core/utils';
import links from 'core/links';
import constants from 'core/constants';
import { NavigationActions} from 'actions/Actions';

const TEMPLATE =
  `<div class="resource" v-on:click="showNetwork($event)">
    <div class="resource-details">
      <img class="resource-icon" src="image-assets/resource-icons/network-small.png"/>
      <div class="resource-label" :title="model.name">{{model.name}}</div>
      <div v-if="!isSystemNetwork && (mode === 'create')" class="resource-actions">
        <a class="btn item-edit" v-on:click="onEdit($event)"><i class="fa fa-pencil"></i></a>
        <a class="btn item-delete" v-on:click="onDelete($event)"><i class="fa fa-trash"></i></a>
      </div>
    </div>
    <div class="resource-anchor-line" v-bind:class="{'shrink': !attached}"></div>
    <div class="resource-anchor">
      <div class="resource-label-drag">{{i18n('app.template.details.network.drag')}}</div>
      <div class="resource-label-drop">{{i18n('app.template.details.network.drop')}}</div>
    </div>
  </div>`;

var removeConfirmationHolder = function($deleteConfirmationHolder) {
  utils.fadeOut($deleteConfirmationHolder, function() {
    $deleteConfirmationHolder.remove();
  });
};

var NetworkBox = Vue.extend({
  template: TEMPLATE,
  props: {
    model: {
      required: true
    },
    mode: {
      required: false,
      default: 'show'
    }
  },
  data: function() {
    return {
      attached: false,
      isConfirmDelete: false
    };
  },
  attached: function() {
    this.attached = true;
    this.$dispatch('attached', this);

    $(this.$el).on('click', '.resource-details .delete-inline-item-confirmation-cancel',
                   this.onDeleteCancel);
    $(this.$el).on('click', '.resource-details .delete-inline-item-confirmation-confirm',
                   this.onDeleteConfirm);
  },
  detached: function() {
    this.$dispatch('detached', this);
  },
  computed: {
    networkId: function() {
      return this.model && this.model.documentSelfLink
                && utils.getDocumentId(this.model.documentSelfLink);
    },
    isSystemNetwork: function() {
      return this.model && this.model.documentSelfLink.indexOf(links.SYSTEM_NETWORK_LINK) === 0;
    },
    isNewNetwork: function() {
      return (this.mode === 'create' && !this.model.external);
    }
  },
  methods: {
    onEdit: function(e) {
      e.preventDefault();
      e.stopImmediatePropagation();
      this.$dispatch('edit', this);
      this.$dispatch('disableNetworkSaveButton', false);
    },
    onDelete: function(e) {
      e.preventDefault();
      e.stopImmediatePropagation();

      this.isConfirmDelete = true;

      var $row = $(this.$el).find('.resource-details');
      var $deleteConfirmationHolder = $(InlineDeleteConfirmationTemplate());
      var $deleteConfirmation = $deleteConfirmationHolder.find('.delete-inline-item-confirmation');
      $deleteConfirmationHolder.height($row.outerHeight(true) + 1);
      $row.append($deleteConfirmationHolder);

      utils.slideToLeft($deleteConfirmation);
    },
    onDeleteCancel: function(e) {
      e.preventDefault();
      e.stopPropagation();

      this.isConfirmDelete = false;

      var $deleteConfirmationHolder = $(e.currentTarget)
        .closest('.delete-inline-item-confirmation-holder');

      removeConfirmationHolder($deleteConfirmationHolder);
    },
    onDeleteConfirm(e) {
      e.preventDefault();
      e.stopPropagation();

      this.isConfirmDelete = false;

      var $deleteConfirmationHolder = $(e.currentTarget)
        .closest('.delete-inline-item-confirmation-holder');

      removeConfirmationHolder($deleteConfirmationHolder);

      this.$dispatch('remove', this);
    },
    showNetwork(e) {
      if (this.isConfirmDelete || this.isNewNetwork) {
        // if network is new - nowhere to navigate to.
         return;
      }

      e.preventDefault();
      e.stopPropagation();

      let queryOptions = {
        $category: constants.RESOURCES.SEARCH_CATEGORY.NETWORKS,
        $occurrence: constants.SEARCH_OCCURRENCE.ANY,
        any: this.model.name
      };

      NavigationActions.openNetworks(queryOptions);
    }
  }
});

Vue.component('network-box', NetworkBox);

export default NetworkBox;
