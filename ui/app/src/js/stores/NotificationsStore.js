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
  RETRIEVE_NOTIFICATIONS: 'RETRIEVE_NOTIFICATIONS'
};

var NotificationsStore = Reflux.createStore({
  mixins: [CrudStoreMixin],

  listenables: [actions.NotificationsActions],

  onRetrieveNotifications: function() {
    var operation = this.requestCancellableOperation(OPERATION.RETRIEVE_NOTIFICATIONS);
    if (operation) {
      operation.forPromise(services.loadNotifications()).then((result) => {
        this.setInData(['latestEventLogItemsCount'], result.recentEventLogsCount);
        this.setInData(['runningRequestItemsCount'], result.activeRequestsCount);
        this.emitChange();
      });
    }
  }
});

export default NotificationsStore;
