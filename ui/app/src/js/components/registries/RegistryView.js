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

import InlineEditableList from 'components/common/InlineEditableList';
import { RegistryActions, RegistryContextToolbarActions } from 'actions/Actions';
import Component from 'components/common/Component';
import RegistryViewTemplate from 'components/registries/RegistryViewTemplate.html';
import RegistryListTemplate from 'components/registries/RegistryListTemplate.html';
import RegistryRowRenderers from 'components/registries/RegistryRowRenderers';
import RegistryRowEditor from 'components/registries/RegistryRowEditor';
import CredentialsList from 'components/credentials/CredentialsList'; //eslint-disable-line
import CertificatesList from 'components/certificates/CertificatesList'; //eslint-disable-line

function RegistryView($el) {
  this.$el = $el;

  $el.html(RegistryViewTemplate());

  var $registyListHolder = $el.find('.list-holder');
  this.registriesList = new InlineEditableList($registyListHolder, RegistryListTemplate,
                                               RegistryRowRenderers);

  this.registriesList.setRowEditor(RegistryRowEditor);
  this.registriesList.setDeleteCallback(RegistryActions.deleteRegistry);
  this.registriesList.setEditCallback(RegistryActions.editRegistry);

  $registyListHolder.find('.inline-editable-list').on('click', '.item .item-enable', function(e) {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');
    var $item = $(e.currentTarget).closest('.item');
    var entity = $item.data('entity');
    RegistryActions.enableRegistry(entity);
  });

  $registyListHolder.find('.inline-editable-list').on('click', '.item .item-disable', function(e) {
    e.preventDefault();

    $(e.currentTarget).addClass('loading');
    var $item = $(e.currentTarget).closest('.item');
    var entity = $item.data('entity');
    RegistryActions.disableRegistry(entity);
  });

  this.contextView = new ContextSidePanel(this.$el);
}

RegistryView.prototype.setData = function(data) {
  if (this.data !== data) {

    // For easier comparisson of properties
    var oldData = this.data || {};

    if (oldData.registries !== data.registries) {
      this.registriesList.setData(data.registries);
    }

    if (oldData.contextView !== data.contextView) {
      updateContextView.call(this, oldData.contextView, data.contextView);
    }
  }
};

var updateContextView = function(oldView, newView) {
  this.contextView.setData(newView);
};

class ContextSidePanel extends Component {
  constructor($el) {
    super();
    this.vue = new Vue({
      el: $el[0],
      data: {
        model: {}
      },
      methods: {
        openToolbarCredentials: RegistryContextToolbarActions.openToolbarCredentials,
        openToolbarCertificates: RegistryContextToolbarActions.openToolbarCertificates,
        closeToolbar: RegistryContextToolbarActions.closeToolbar
      },
      computed: {
        activeContextItem: function() {
          return this.model.activeItem && this.model.activeItem.name;
        },
        contextExpanded: function() {
          return this.model.expanded;
        }
      }
    });
  }

  setData(data) {
    Vue.set(this.vue, 'model', data);
  }
}


var RegistryViewVue = Vue.extend({
  template: '<div></div>',
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  attached: function() {
    var registryView = new RegistryView($(this.$el));
    this.unwatchModel = this.$watch('model', (data) => {
      if (data) {
        registryView.setData(data);
      }
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
  }
});

Vue.component('registry-view', RegistryViewVue);

export default RegistryView;
