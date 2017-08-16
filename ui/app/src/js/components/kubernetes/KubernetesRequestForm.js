/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import KubernetesRequestFormVue from 'components/kubernetes/KubernetesRequestFormVue.html';
import KubernetesDefinitionForm from 'components/kubernetes/KubernetesDefinitionForm';
import { KubernetesActions } from 'actions/Actions';

var KubernetesRequestForm = Vue.extend({
  template: KubernetesRequestFormVue,
  props: {
    model: {
      required: true,
      type: Object
    },
    fromResource: {
      type: Boolean
    }
  },
  data: function() {
    return {
      creating: false,
      entitiesContent: ''
    };
  },
  computed: {
    disableCreatingButton: function() {
      var content = this.entitiesContent && this.entitiesContent.trim();
      return this.creating || !content;
    }
  },
  methods: {
    handleContentChange: function(content) {
      this.entitiesContent = content;
    },
    createEntities: function() {
      var content = this.entitiesContent && this.entitiesContent.trim();
      if (content) {
        this.creating = true;
        KubernetesActions.createKubernetesEntities(content);
      }
    }
  },
  components: {
    kubernetesDefinitionForm: KubernetesDefinitionForm
  }
});

export default KubernetesRequestForm;
