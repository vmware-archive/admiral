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

import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin';
import ProfileItemVue from 'components/profiles/ProfileItemVue.html';
import { ProfileActions, NavigationActions } from 'actions/Actions';
import utils from 'core/utils';

export default Vue.component('profile-grid-item', {
  template: ProfileItemVue,
  mixins: [DeleteConfirmationSupportMixin],
  props: {
    model: {
      required: true
    }
  },
  computed: {
    endpointName() {
      return (this.model.endpoint && this.model.endpoint.name) || '';
    },
    endpointType() {
      return (this.model.endpoint && this.model.endpoint.endpointType) || this.model.endpointType;
    },
    iconSrc() {
      return utils.getAdapter(this.endpointType).iconSrc;
    }
  },
  methods: {
    removeProfile() {
      this.confirmRemoval(ProfileActions.deleteProfile, [this.model]);
    },
    editProfile(event) {
      event.preventDefault();

      NavigationActions.editProfile(utils.getDocumentId(this.model.documentSelfLink));
    }
  }
});
