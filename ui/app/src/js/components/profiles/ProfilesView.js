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

import ProfilesViewVue from 'components/profiles/ProfilesViewVue.html';
import ProfileItem from 'components/profiles/ProfileItem'; //eslint-disable-line
import ProfileEditView from 'components/profiles/ProfileEditView'; //eslint-disable-line
import InstanceTypeEditView from 'components/profiles/instance-types/InstanceTypeEditView'; //eslint-disable-line
import GridHolderMixin from 'components/common/GridHolderMixin';
import constants from 'core/constants';
import { ProfileActions, NavigationActions } from 'actions/Actions';

var ProfilesView = Vue.extend({
  template: ProfilesViewVue,

  props: {
    model: {
      required: true,
      type: Object
    }
  },

  data: function() {
    return {
      constants: constants,
      // this view behaves better if the target width is set before the width transition
      requiresPreTransitionWidth: true
    };
  },

  mixins: [GridHolderMixin],

  attached: function() {
    var $mainPanel = $(this.$el).find('.list-holder > .main-panel');
    $mainPanel.on('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd',
      (e) => {
        if (e.target === $mainPanel[0]) {
          this.unsetPostTransitionGridTargetWidth();
        }
      }
    );
  },

  detached: function() {
    var $mainPanel = $(this.$el).find('.list-holder > .main-panel');
    $mainPanel.off('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd');
  },

  computed: {
    searchSuggestions: function() {
      return constants.PROFILES.SEARCH_SUGGESTIONS;
    }
  },

  methods: {
    goBack: function() {
      NavigationActions.openProfiles(this.model.listView && this.model.listView.queryOptions);
    },
    search: function(queryOptions) {
      NavigationActions.openProfiles(queryOptions);
    },
    refresh: function() {
      ProfileActions.openProfiles(this.model.listView.queryOptions, true);
    },
    loadMore: function() {
      if (this.model.listView.nextPageLink) {
        ProfileActions.openProfilesNext(this.model.listView.queryOptions,
          this.model.listView.nextPageLink);
      }
    },
    addProfile: function() {
      NavigationActions.openAddProfile();
    },
    editProfile: function(profile) {
      NavigationActions.editProfile(profile.documentSelfLink);
    }
  }
});

Vue.component('profiles-view', ProfilesView);

export default ProfilesView;
