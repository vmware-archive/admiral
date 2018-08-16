/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import RequestsListVue from 'components/requests/RequestsListVue.html';
import RequestsListItemVue from 'components/requests/RequestsListItemVue.html';
import Component from 'components/common/Component';
import InlineDeleteConfirmationTemplate from
  'components/common/InlineDeleteConfirmationTemplate.html';
import { RequestsActions, AppActions, NavigationActions } from 'actions/Actions';
import constants from 'core/constants';
import utils from 'core/utils';
import links from 'core/links';
import ft from 'core/ft';

var RequestsListVueComponent = Vue.extend({
  template: RequestsListVue,
  props: {
    model: {required: true}
  },
  components: {
    'request-item': {
      template: RequestsListItemVue,
      props: {
        model: {required: true}
      },
      computed: {
        progress: function() {
          return this.model.taskInfo.stage === 'FAILED' ? 100 : this.model.progress;
        },
        resourceIds: function() {
          return this.model.resourceLinks && this.model.resourceLinks.map((resourceLink) => {
            return utils.getDocumentId(resourceLink);
          });
        },
        hasResourceIds: function() {
          return this.resourceIds && this.resourceIds.length > 0;
        },
        requestTitleText: function() {
          if (this.hasResourceIds) {
            return this.resourceIds;
          } else {
            return this.model.name;
          }
        },
        requestId: function() {
          return utils.getDocumentId(this.model.documentSelfLink);
        },
        isRequestGraphEnabled: function() {
          return ft.isRequestGraphEnabled();
        }
      },
      data: function() {
        return {
          expanded: false,
          showHint: false
        };
      },
      attached: function() {
        // Handle Confirm Request Item Delete
        $(this.$el).on('click', '.delete-inline-item-confirmation-confirm', (e) => {
          e.preventDefault();

          // Hide cancel button and show loading indication
          var $deleteButton = $(e.currentTarget);
          $deleteButton.prev('.delete-inline-item-confirmation-cancel').addClass('hide');
          $deleteButton.css('float', 'right');
          $deleteButton.find('.fa').removeClass('hide');

          RequestsActions.removeRequest(this.model.documentSelfLink);
        });

        // Handle Cancel Request Item Delete
        $(this.$el).on('click', '.delete-inline-item-confirmation-cancel', (e) => {
          e.preventDefault();
          this.hideDeleteConfirmationButtons(e);
        });

        this.$watch('progress', (progress, oldProgress) => {
          if (progress === 100 && oldProgress < 100) {
            this.showHint = true;

            setTimeout(() => {
              this.showHint = false;
            }, 3000);
          }
        });
      },
      methods: {
        isRequestRunning: function(item) {
          return utils.isRequestRunning(item);
        },

        isRequestFailed: function(item) {
          return utils.isRequestFailed(item);
        },

        isRequestFinished: function(item) {
          return utils.isRequestFinished(item);
        },

        isEnabled(item) {
          return this.isRequestFinished(item) ||
            (this.isRequestFailed(item) && item.eventLogLink);
        },

        isDeleteEnabled(item) {
          return !this.isRequestRunning(item);
        },

        getProgressClass: function(item) {
          if (utils.isRequestFailed(item)) {
            return ['progress-bar-danger'];
          } else if (utils.isRequestRunning(item)) {
            return ['progress-bar-info', 'progress-bar-striped', 'active'];
          } else {
            return ['progress-bar-success'];
          }
        },

        isRedirectionAllowed() {
            return utils.actionAllowed(window.routesRestrictions.DEPLOYMENTS);
        },

        isKubernetesDeploymentOrCluster() {
          if (!this.model.resourceLinks || this.model.resourceLinks.length === 0) {
            return false;
          }

          let link = this.model.resourceLinks[0];
          let isKubernetesDeployment = link.indexOf(links.KUBERNETES_DEPLOYMENTS) !== -1;
          let isCluster = link.indexOf(links.CONTAINER_CLUSTERS) !== -1;

          return isKubernetesDeployment || isCluster;
        },

        redirect($e) {
          $e.preventDefault();

          if (this.isRequestFinished(this.model)
            && this.isRedirectionAllowed()
            && !this.isKubernetesDeploymentOrCluster()) {

            let isHostsRequest = false;
            let hostsQuery;
            let closureId;
            if (this.model.resourceLinks) {
              var link = this.model.resourceLinks[0];
              if (link.indexOf(links.COMPUTE_RESOURCES) !== -1) {
                isHostsRequest = true;
                hostsQuery = {
                  documentId: utils.getDocumentId(link)
                };
              }
              if (link.indexOf(links.CLOSURES) !== -1) {
                closureId = utils.getDocumentId(link);
              }
            } else if (this.model.name === 'Configure Host') {
              isHostsRequest = true;
            }

            if (isHostsRequest) {
              NavigationActions.openHosts(hostsQuery);
            } else if (closureId) {
              NavigationActions.openClosureDetails(closureId);
            } else {
              NavigationActions.openContainers(this.getRequestResourceQueryOpts(this.model));
            }

            return;
          }

          if (this.isRequestFailed(this.model) && this.model.eventLogLink) {
            AppActions.openToolbarEventLogs(this.model.eventLogLink);
            return;
          }
        },

        openRequestGraph: function($e) {
          $e.preventDefault();
          var graphPath = 'index-single-view.html#/request-graph/' + this.requestId;
          var win = window.open(graphPath, '_blank');
          win.focus();
        },

        getRequestResourceQueryOpts: function(item) {
          var params = {};

          if (item.resourceLinks) {
            params[constants.SEARCH_OCCURRENCE.PARAM] = constants.SEARCH_OCCURRENCE.ANY;
            for (var i = 0; i < item.resourceLinks.length; i++) {
              var link = item.resourceLinks[i];
              if (link) {
                params.documentId = utils.getDocumentId(link);

                if (link.indexOf(links.COMPOSITE_COMPONENTS) !== -1) {
                  // Applications
                  params[constants.SEARCH_CATEGORY_PARAM] =
                      constants.RESOURCES.SEARCH_CATEGORY.APPLICATIONS;
                } else if (link.indexOf(links.CONTAINERS) !== -1) {
                  // Containers
                  params[constants.SEARCH_CATEGORY_PARAM] =
                      constants.RESOURCES.SEARCH_CATEGORY.CONTAINERS;
                } else if (link.indexOf(links.CONTAINER_NETWORKS) !== -1) {
                  // Networks
                  params[constants.SEARCH_CATEGORY_PARAM] =
                      constants.RESOURCES.SEARCH_CATEGORY.NETWORKS;
                } else if (link.indexOf(links.CONTAINER_VOLUMES) !== -1) {
                  // Volumes
                  params[constants.SEARCH_CATEGORY_PARAM] =
                    constants.RESOURCES.SEARCH_CATEGORY.VOLUMES;
                }
              }
            }
          }

          return params;
        },

        deleteRequest: function($event) {
          $event.stopPropagation();
          $event.preventDefault();

          let $requestItemHolder = $(this.$el);
          let $requestItem = $requestItemHolder.find('.request-item');
          let $deleteConfirmationHolder = $(InlineDeleteConfirmationTemplate());
          $deleteConfirmationHolder.height($requestItem.outerHeight(true));
          $deleteConfirmationHolder.insertAfter($requestItem);

          let $deleteConfirmation = $deleteConfirmationHolder
                                      .find('.delete-inline-item-confirmation');
          utils.slideToLeft($deleteConfirmation);
        },

        hideDeleteConfirmationButtons: function($event) {
          $event.stopPropagation();
          $event.preventDefault();

          let $deleteConfirmationHolder = $($event.currentTarget)
            .closest('.delete-inline-item-confirmation-holder');

          utils.fadeOut($deleteConfirmationHolder, function() {
            $deleteConfirmationHolder.remove();
          });

          $(this.$el).find('.request-status-actions').removeClass('hide');
        }
      }
    }
  },

  attached: function() {
    $(this.$el).find('.nav-item:first-of-type .nav-link').tab('show');
  },

  methods: {
    detached: function() {
      RequestsActions.closeRequests();
    },

    requestRunningFilter: function(item) {
      return utils.isRequestRunning(item);
    },

    requestFailedFilter: function(item) {
      return utils.isRequestFailed(item);
    },

    selectTab($event, itemsType) {
      $event.stopPropagation();
      $event.preventDefault();
      $($event.target).tab('show');
      RequestsActions.selectRequests(itemsType);
    },

    loadMore: function(itemsType) {
      if (this.model.nextPageLink && itemsType === this.model.itemsType) {
        RequestsActions.openRequestsNext(this.model.nextPageLink);
      }
    },

    close: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      this.$dispatch('close');
    },

    actionAllowed: function() {
      return utils.actionAllowed(window.routesRestrictions.REQUESTS_DELETE);
    }
  },
  events: {
    'do-action': function(actionName) {

      if (actionName === 'refresh') {
        // Refresh
        RequestsActions.refreshRequests();
      } else if (actionName === 'deleteAll') {
        // Clear all requests
        RequestsActions.clearRequests();
      }
    }
  }
});

Vue.component('requests-list', RequestsListVueComponent);

class RequestsList extends Component {
  constructor() {
    super();

    this.$el = $('<div><requests-list v-bind:model="model"></requests-list></div>');

    this.vue = new Vue({
      el: this.$el[0],
      data: {
        model: {}
      }
    });
  }

  setData(data) {
    Vue.set(this.vue, 'model', data);
  }

  getEl() {
    return this.$el;
  }
}

export default RequestsList;
