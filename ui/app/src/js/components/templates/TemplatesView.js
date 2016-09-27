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

import TemplatesViewVue from 'TemplatesViewVue';
import ListItemImageVue from 'ListItemImageVue';
import ListItemContainerVue from 'ListItemContainerVue';
import TemplateDetailsView from 'components/templates/TemplateDetailsView'; // eslint-disable-line
import RegistryView from 'components/registries/RegistryView'; // eslint-disable-line
import TemplateImporterView from 'components/templates/TemplateImporterView'; // eslint-disable-line
import ContainerRequestForm from 'components/containers/ContainerRequestForm'; // eslint-disable-line
import RequestsList from 'components/requests/RequestsList';//eslint-disable-line
import EventLogList from 'components/eventlog/EventLogList';//eslint-disable-line
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin'; //eslint-disable-line
import VueDeleteItemConfirmation from 'components/common/VueDeleteItemConfirmation'; //eslint-disable-line
import VueAdapter from 'components/common/VueAdapter';
import ResourceGroupsMixin from 'components/templates/ResourceGroupsMixin'; // eslint-disable-line
import GridHolderMixin from 'components/common/GridHolderMixin';
import constants from 'core/constants';
import utils from 'core/utils';
import exportHelper from 'components/templates/TemplateExportHelper';
import { NavigationActions, RequestsActions, NotificationsActions,
          TemplateActions, TemplatesContextToolbarActions } from 'actions/Actions';

var TemplatesViewVueComponent = Vue.extend({
  template: TemplatesViewVue,
  props: {
    model: {
      required: true,
      type: Object,
      default: () => {
        return {
          listView: {},
          contextView: {}
        };
      }
    }
  },
  data: function() {
    var placeholderByCategory = {};
    placeholderByCategory[constants.TEMPLATES.SEARCH_CATEGORY.ALL] =
      i18n.t('app.template.list.searchImagesTemplatesPlaceholder');
    placeholderByCategory[constants.TEMPLATES.SEARCH_CATEGORY.IMAGES] =
      i18n.t('app.template.list.searchImagesPlaceholder');
    placeholderByCategory[constants.TEMPLATES.SEARCH_CATEGORY.TEMPLATES] =
      i18n.t('app.template.list.searchTemplatesPlaceholder');

    var alertData = {};
    alertData.show = false;
    alertData.message = '';
    alertData.type = 'danger';

    return {
      constants: constants,
      queryOptions: {},
      placeholderByCategory: placeholderByCategory,
      // this view behaves better if the target width is set before the width transition
      requiresPreTransitionWidth: true,
      alert: alertData
    };
  },
  computed: {
    activeContextItem: function() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded: function() {
      return this.model.contextView && this.model.contextView.expanded;
    },
    queryOptions: function() {
      return this.model.listView && this.model.listView.queryOptions;
    },
    selectedCategory: function() {
      var queryOpts = this.queryOptions || {};
      return queryOpts[constants.SEARCH_CATEGORY_PARAM] ||
        constants.CONTAINERS.SEARCH_CATEGORY.ALL;
    },
    requestsCount: function() {
      var contextView = this.model.contextView;
      if (contextView && contextView.notifications) {
        return contextView.notifications.requests;
      }
      return 0;
    },
    eventLogsCount: function() {
      var contextView = this.model.contextView;
      if (contextView && contextView.notifications) {
        return contextView.notifications.eventlogs;
      }
      return 0;
    },
    showContextPanel: function() {
      return !this.model.registries
        && !this.model.importTemplate
        && (!this.model.selectedItemDetails
          || this.model.selectedItemDetails && this.model.selectedItemDetails.selectedForEdit
              && !this.model.selectedItemDetails.editContainerDefinition
              && !this.model.selectedItemDetails.newContainerDefinition
              && !this.model.selectedItemDetails.editNetwork);
    }
  },
  mixins: [GridHolderMixin],
  attached: function() {
    var $mainPanel = $(this.$el).children('.list-holder').children('.main-panel');

    $mainPanel.on('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd',
      (e) => {
        if (e.target === $mainPanel[0]) {
          this.unsetPostTransitionGridTargetWidth();
        }
      }
    );

    this.unwatchExpanded = this.$watch('contextExpanded', () => {
      Vue.nextTick(() => {
        this.setPreTransitionGridTargetWidth($mainPanel);
      });
    });

    this.refreshRequestsInterval = setInterval(() => {
      if (this.activeContextItem === constants.CONTEXT_PANEL.REQUESTS) {
        RequestsActions.refreshRequests();
      }
    }, constants.REQUESTS.REFRESH_INTERVAL);

    this.notificationsInterval = setInterval(() => {
      if (!this.contextExpanded && !this.model.registries) {
        NotificationsActions.retrieveNotifications();
      }
    }, constants.NOTIFICATIONS.REFRESH_INTERVAL);

    $(this.$el).find('.import-template i').tooltip({html: true});
  },
  detached: function() {
    this.unwatchExpanded();

    var $mainPanel = $(this.$el).children('.list-holder').children('.main-panel');
    $mainPanel.off('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd');

    clearInterval(this.refreshRequestsInterval);
    clearInterval(this.notificationsInterval);
  },
  components: {
    'container-image-item': {
      template: ListItemImageVue,
      mixins: [ResourceGroupsMixin],
      props: {
        model: {required: true},
        groups: {required: false}
      },
      methods: {
        provisionContainer: function($event) {
          $event.stopPropagation();
          $event.preventDefault();

          if ($event.shiftKey) {
            this.showGroupForProvisioning = false;
          } else {
            this.handleGroup(TemplateActions.createContainer,
              [this.model.type, this.model.documentId]);
          }
        },
        provisionContainerAdditionalInfo: function($event) {
          $event.stopPropagation();
          $event.preventDefault();

          NavigationActions.openContainerRequest(this.model.type, this.model.documentId);
        }
      }
    },
    'container-template-item': {
      template: ListItemContainerVue,
      mixins: [DeleteConfirmationSupportMixin, ResourceGroupsMixin],
      props: {
        model: {required: true},
        groups: {required: false}
      },
      methods: {
        createTemplate: function($event) {
          // this does the provisioning of the Application
          $event.stopPropagation();
          $event.preventDefault();

          if ($event.shiftKey) {
            this.showGroupForProvisioning = false;
          } else {
            var template = {
              'documentSelfLink': this.model.documentSelfLink
            };

            this.handleGroup(TemplateActions.copyTemplate, [this.model.type, template]);
          }
        },
        editTemplate: function($event) {
          $event.stopPropagation();
          $event.preventDefault();

          NavigationActions.openTemplateDetails(this.model.type, this.model.documentId);
        },
        removeTemplateDefinition: function() {
          this.confirmRemoval(TemplateActions.removeTemplate, [this.model.documentId]);
        },
        publishTemplateToCatalog: function($event) {
          $event.stopPropagation();
          $event.preventDefault();

          TemplateActions.publishTemplate(this.model.documentId);
        },
        exportTemplate: function($event) {
          $event.stopPropagation();
          $event.preventDefault();

          exportHelper.showExportDialog(
            this.getExportLink(constants.TEMPLATES.EXPORT_FORMAT.COMPOSITE_BLUEPRINT),
            this.getExportLink(constants.TEMPLATES.EXPORT_FORMAT.DOCKER_COMPOSE));
        },
        getExportLink: function(format) {
          return utils.getExportLinkForTemplate(this.model.documentId, format);
        },
        operationSupported: function(op) {
          return utils.operationSupportedTemplate(op);
        }
      }
    }
  },
  methods: {
    goBack: function() {
      let detailsView = this.$refs.templateDetails;
      if (!detailsView || !detailsView.handleBackButton || !detailsView.handleBackButton()) {
        // Current view cannot handle back button, we will make the navigation
        NavigationActions.openTemplates(this.queryOptions);
      }
    },

    search: function(queryOptions) {
      this.doSearchAndFilter(queryOptions, this.selectedCategory);
    },

    selectCategory(categoryName, $event) {
      this.doSearchAndFilter(this.queryOptions, categoryName);
      $event.stopPropagation();
      $event.preventDefault();
    },

    doSearchAndFilter: function(queryOptions, categoryName) {
      var queryOptionsToSend = $.extend({}, queryOptions);
      queryOptionsToSend[constants.SEARCH_CATEGORY_PARAM] = categoryName;
      NavigationActions.openTemplates(queryOptionsToSend);
    },

    refresh: function() {
      TemplateActions.openTemplates(this.queryOptions, true);
    },

    openToolbarRequests: TemplatesContextToolbarActions.openToolbarRequests,
    openToolbarEventLogs: TemplatesContextToolbarActions.openToolbarEventLogs,
    closeToolbar: TemplatesContextToolbarActions.closeToolbar,

    alertType: function(alert) {
      return alert && alert.type;
    }
  },
  events: {
    'container-form-alert': function(alertMessage, type) {
      if (alertMessage) {
        this.alert.show = true;
        this.alert.message = alertMessage;
        if (type && (type !== 'fail')) {
          this.alert.type = type;
        }
      } else {
        this.alert.show = false;
        this.alert.message = '';
      }
    }
  }
});

const TAG_NAME = 'templates-view';
Vue.component(TAG_NAME, TemplatesViewVueComponent);

function TemplatesView($el) {
  return new VueAdapter($el, TAG_NAME);
}


export default TemplatesView;
