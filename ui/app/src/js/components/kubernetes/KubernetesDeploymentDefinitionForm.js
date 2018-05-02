/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import KubernetesDeploymentDefinitionFormTemplate from
  'components/kubernetes/KubernetesDeploymentDefinitionFormTemplate.html';
import Component from 'components/common/Component';
import utils from 'core/utils';
import imageUtils from 'core/imageUtils';
import definitionFormUtils from 'core/definitionFormUtils';

const DEFAULT_TAG = 'latest';

let containerDescriptionConstraints = {
  image: definitionFormUtils.containerDescriptionConstraints().image,
  name: definitionFormUtils.containerDescriptionConstraints().name,
  _cluster: definitionFormUtils.containerDescriptionConstraints().__cluster
};

class KubernetesDeploymentDefinitionForm extends Component {
  constructor() {
    super();

    this.$el = $(KubernetesDeploymentDefinitionFormTemplate());

    this.$el.find('.fa-question-circle').tooltip({html: true});

    this.$imageSearch = this.$el.find('.container-image-input .image-name-input .form-control');
    this.$imageSearch.typeahead({}, {
      name: 'images',
      source: definitionFormUtils.typeaheadSource(
        this.$el.find('.container-image-input .image-name-input'))
    });

    this.$imageTags = this.$el.find('.container-image-input .image-tags-input .form-control');
    definitionFormUtils.setTagsTypeahead(this.$imageTags, [DEFAULT_TAG]);

    this.$tagsHolder = this.$el.find('.container-image-input .image-tags-input');
    this.$imageSearch.bind(
      'typeahead:selected', definitionFormUtils.typeaheadTagsLoader(this.$tagsHolder));

    this.$imageSearch.blur(() => {
      var image = this.$imageSearch.typeahead('val');
      var tag = this.$imageTags.typeahead('val');
      definitionFormUtils.loadTags(this.$tagsHolder, image, tag);
    });

    this.$el.find('.nav-item a[href="#basic"]').tab('show');
    this.$el.find('#basic.tab-pane').addClass('active');
  }

  setData(data) {
    if (this.data !== data) {
      updateForm.call(this, data, this.data || {});

      this.data = data;
    }
  }

  getEl() {
    return this.$el;
  }

  getRawInput() {
    var result = {};
    result.image = this.$imageSearch.typeahead('val');
    var tag = this.$imageTags.typeahead('val');
    if (tag) {
      result.image += ':' + tag;
    }
    result.name = validator.trim(this.$el.find('.container-name-input .form-control').val());
    result._cluster = this.$el.find('.container-cluster-size-input .form-control').val() || 1;
    return result;
  }

  /* convert a raw input object to DTO or read it from view */
  getContainerDescription(rawInput) {
    var result = rawInput || this.getRawInput();
    console.log('getContainerDescription');

    var currentData = this.data || {};

    // If data was set use the noneditable values
    result.documentId = currentData.documentId;
    result.documentSelfLink = currentData.documentSelfLink;

    return result;
  }

  validate() {
    var rawInput = this.getRawInput();
    var validationErrors = utils.validate(rawInput, containerDescriptionConstraints);

    return validationErrors;
  }

  applyValidationErrors(errors) {
    errors = errors || {};

    var image = this.$el.find('.container-image-input');
    utils.applyValidationError(image, errors.image);

    var name = this.$el.find('.container-name-input');
    utils.applyValidationError(name, errors.name);
    this.switchTabs(errors);
  }

  switchTabs(errors) {
    var tabsToActivate = [];

    var fillTabsToActivate = ($el) => {
      let tabId = this.getTabId($el);
      if (tabsToActivate.indexOf(tabId) === -1) {
        tabsToActivate.push(tabId);
      }
    };

    if (errors.image) {
      fillTabsToActivate(this.$el.find('.container-image-input'));
    }
    if (errors.name) {
      fillTabsToActivate(this.$el.find('.container-name-input'));
    }
    if (errors._cluster) {
      fillTabsToActivate(this.$el.find('.container-cluster-size-input'));
    }

    var activeTabId = this.getActiveTabId();
    if (tabsToActivate.length > 0 && (!activeTabId || tabsToActivate.indexOf(activeTabId) === -1)) {
      this.activateTab(tabsToActivate[0]);
    }
  }

  getTabId($inputEl) {
    return $inputEl.closest('.tab-pane').attr('id');
  }

  getActiveTabId() {
    return $(this.$el).find('.container-form-content .tab-content .tab-pane.active').attr('id');
  }

  activateTab(tabId) {
    $(this.$el).find('.container-form-content .nav a[href="#' + tabId + '"]').tab('show');
  }
}

var updateForm = function(data, oldData) {
  if (data.image !== oldData.image) {
    var tag = imageUtils.getImageTag(data.image);
    if (tag) {
      this.$imageSearch.typeahead('val', data.image.slice(0, data.image.lastIndexOf(':')));
      this.$imageTags.typeahead('val', tag);
    } else {
      this.$imageSearch.typeahead('val', data.image);
    }

    definitionFormUtils.loadTags(this.$tagsHolder, data.image, tag);
  }

  if (data.name !== oldData.name) {
    this.$el.find('.container-name-input .form-control').val(data.name);
  }

  if (data._cluster !== oldData._cluster) {
    this.$el.find('.container-cluster-size-input .form-control').val(data._cluster);
  }
};

export default KubernetesDeploymentDefinitionForm;
