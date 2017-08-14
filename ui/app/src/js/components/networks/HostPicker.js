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

import HostPickerVue from 'components/networks/HostPickerVue.html';
import { DropdownSearchMenu } from 'admiral-ui-common';
import utils from 'core/utils';
import services from 'core/services';

const HOST_RESULT_LIMIT = 10;

const INITIAL_FILTER = '';
var initialQueryPromise;

var HOST_DROPDOWN_RENDERER = function(host) {
  var hostName = host.displayName || utils.getHostName(host);

  return `
    <div>
      <div class="host-picker-item-primary" title="${hostName}">${hostName}</div>
      <div class="host-picker-item-secondary" title="${host.address}">(${host.address})</div>
    </div>`;
};


function hostSearchCallback(q, callback) {
  var promise;
  if (!q) {
    promise = initialQueryPromise;
  } else {
    promise = services.loadClusters(q, HOST_RESULT_LIMIT);
  }
  promise.then(function(result) {
    var hostResult = {};
    hostResult.items = [];
    hostResult.totalCount = 0;
    for (var i = 0; i < result.items.length; i++) {
      var hostsInCluster = [];
      var currentCluster = result.items[i];
      var currentCounter = currentCluster.nodeLinks.length;
      for (var j = 0; j < currentCluster.nodeLinks.length; j++) {
        var host = currentCluster.nodes[currentCluster.nodeLinks[j]];
        host.displayName = host.address + ' (' + currentCluster.name + ')';
        hostsInCluster.push(host);
      }
      hostResult.items.push.apply(hostResult.items, hostsInCluster);
      hostResult.totalCount += currentCounter;
    }
    callback(hostResult);
  });
}

var HostPicker = Vue.extend({
  template: HostPickerVue,
  props: {
    viewHosts: {
      required: false,
      type: Object
    },
    multiSelection: {
      required: false,
      type: Boolean,
      default: true
    }
  },
  methods: {
    getHosts: function() {
      var hosts = [];

      var hostSearchComps = this.$children;
      for (var i = 0; i < hostSearchComps.length; i++) {
        var hostSearchComp = hostSearchComps[i];
        if (hostSearchComp.getHost) {
          var host = hostSearchComp.getHost();
          if (host) {
            hosts.push(host);
          }
        }
      }
      return hosts;
    },
    addHost: function() {
      this.viewHosts.push({
        viewId: utils.uuid()
      });
    },
    removeHost: function(uuid) {
      if (this.viewHosts.length > 1) {
        this.viewHosts = this.viewHosts.filter(vh => vh.viewId !== uuid);
      }
    }
  },
  attached: function() {
    this.viewHosts = [{
      viewId: utils.uuid()
    }];

    initialQueryPromise = services.loadClusters(INITIAL_FILTER, HOST_RESULT_LIMIT);
  },
  detached: function() {
  },
  components: {
    hostSearch: {
      template: '<div></div>',
      attached: function() {
        this.hostInput = new DropdownSearchMenu($(this.$el), {
          title: i18n.t('dropdownSearchMenu.title', {
            entity: i18n.t('app.host.entity')
          }),
          searchPlaceholder: i18n.t('app.template.details.editNetwork.hostsSearchPlaceholder')
        });
        this.hostInput.setOptionsRenderer(HOST_DROPDOWN_RENDERER);
        this.hostInput.setOptionSelectCallback(() => toggleChanged.call(this));
        this.hostInput.setClearOptionSelectCallback(() => toggleChanged.call(this));
        this.hostInput.setFilterCallback(hostSearchCallback);
        this.hostInput.setFilter(INITIAL_FILTER);
      },
      methods: {
        getHost: function() {
          return this.hostInput.getSelectedOption();
        }
      }
    }
  }
});

var toggleChanged = function() {
  this.$dispatch('change');
};

export default HostPicker;
