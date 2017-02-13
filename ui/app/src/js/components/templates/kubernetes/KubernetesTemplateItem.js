/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import KubernetesTemplateItemVue from
  'components/templates/kubernetes/KubernetesTemplateItemVue.html';
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin';
import { TemplateActions } from 'actions/Actions';


var KubernetesTemplateItem = Vue.extend({
  template: KubernetesTemplateItemVue,
  mixins: [DeleteConfirmationSupportMixin],
  props: {
    model: {
      required: true,
      type: Object
    },
    templateId: {
      required: true,
      type: String
    }
  },

  attached: function() {
    this.$dispatch('attached', this);
  },
  detached: function() {
    this.$dispatch('detached', this);
  },

  methods: {
    editKubernetesDescription: function(e) {
      if (e != null) {
          e.preventDefault();
      }

      TemplateActions.openEditKubernetesDefinition(this.model);
    },

    removeKubernetesDescription: function() {
      TemplateActions.removeKubernetesDefinition(this.model, this.templateId);
    }
  }
});

export default KubernetesTemplateItem;
