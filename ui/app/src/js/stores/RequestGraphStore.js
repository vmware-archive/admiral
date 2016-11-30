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

import * as actions from 'actions/Actions';
import services from 'core/services';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

let RequestsGraphStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [actions.RequestGraphActions],

  onOpenRequestGraph: function(requestId, host) {
    services.loadRequestGraph(requestId, host).then((graph) => {
      this.setInData(['graph'], graph);
      this.emitChange();
    });
  }
});

export default RequestsGraphStore;
