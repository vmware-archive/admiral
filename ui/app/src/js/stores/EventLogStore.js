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

import services from 'core/services';
import * as actions from 'actions/Actions';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

const OPERATION = {
  LIST: 'LIST_EVENTS',
  GET_NOTIFICATIONS: 'GET_NOTIFICATIONS'
};

var mergeItems = function(items1, items2) {
  return items1.concat(items2).filter((item, index, self) =>
      self.findIndex((c) => c.documentSelfLink === item.documentSelfLink) === index);
};

var EventLogStore = Reflux.createStore({
  mixins: [CrudStoreMixin],

  listenables: [actions.EventLogActions],

  onOpenEventLog: function(highlightedItemLink) {
    if (!this.data.itemsType) {
      this.setInData(['itemsType'], 'all');
    }
    this.setInData(['itemsLoading'], true);

    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      operation.forPromise(services.loadEventLogs(this.data.itemsType)).then((result) => {
        var items = result.documentLinks.map((documentLink) => result.documents[documentLink]);
        var highlighted = items.find((item) => item.documentSelfLink === highlightedItemLink);
        if (!highlightedItemLink || highlighted) {
          if (highlighted) {
            highlighted.isHighlighted = true;
          }

          this.setInData(['itemsLoading'], false);
          this.setInData(['items'], items);
          this.setInData(['nextPageLink'], result.nextPageLink);
          this.emitChange();
        } else {
          this.onOpenEventLogNext(result.nextPageLink, highlightedItemLink);
        }
      });
    }

    this.emitChange();
  },

  onOpenEventLogNext: function(nextPageLink, highlightedItemLink) {
    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['itemsLoading'], true);
      operation.forPromise(services.loadNextPage(nextPageLink)).then((result) => {
        var items = mergeItems(this.data.items.asMutable(),
            result.documentLinks.map((documentLink) => result.documents[documentLink]));
        var highlighted = items.find((item) => item.documentSelfLink === highlightedItemLink);
        if (!highlightedItemLink || highlighted) {
          if (highlighted) {
            highlighted.isHighlighted = true;
          }
          this.setInData(['itemsLoading'], false);
          this.setInData(['items'], items);
          this.setInData(['nextPageLink'], result.nextPageLink);
          this.emitChange();

        } else {
          this.onOpenEventLogNext(result.nextPageLink, highlightedItemLink);
        }
      });
    }

    this.emitChange();
  },

  onCloseEventLog() {
    this.setInData(['items'], []);
    this.setInData(['itemsLoading'], false);
    this.setInData(['nextPageLink'], null);
  },

  onSelectEventLog(itemsType) {
    this.setInData(['items'], []);
    this.setInData(['itemsType'], itemsType);

    this.onOpenEventLog();
  },

  onRemoveEventLog: function(eventLogSelfLink) {

    services.removeEventLog(eventLogSelfLink).then(() => {
      var eventLogs = this.data.items.asMutable();

      for (var i = 0; i < eventLogs.length; i++) {
        if (eventLogs[i].documentSelfLink === eventLogSelfLink) {
          eventLogs.splice(i, 1);
        }
      }

      this.setInData(['items'], eventLogs);
      this.emitChange();
    }).catch((error) => {
      console.log('Could not delete event log: ' + eventLogSelfLink
                    + '. Failed with: ' + error);
    });
  },

  onClearEventLog: function() {

    services.clearEventLog().then(() => {
      this.setInData(['items'], []);
      this.emitChange();
    }).catch((error) => {
      console.log('Could not clear event log: ' + error);
    });
  },

  onRetrieveEventLogNotifications: function(eventLogsCount) {
    if (eventLogsCount !== undefined) {
      this.setInData(['latestItemsCount'], eventLogsCount);
      this.emitChange();
    }
  }
});

export default EventLogStore;
