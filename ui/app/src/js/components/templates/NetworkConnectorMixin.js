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

import utils from 'core/utils';

var distance = function(x1, y1, x2, y2) {
  return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
};

var anchorSelector = function(xy, wh, txy, twh, anchors) {
  var left = txy[0] + twh[0] < xy[0];
  var right = xy[0] + wh[0] < txy[0];
  var top = txy[1] + twh[1] < xy[1];
  var bottom = xy[1] + wh[1] < txy[1];

  var cx = 0;
  var cy = 0;
  if (top) {
    cy = txy[1] + twh[1];
  } else if (bottom) {
    cy = txy[1];
  } else {
    cy = txy[1];
  }

  if (left) {
    cx = txy[0] + twh[0];
  } else if (right) {
    cx = txy[0];
  } else {
    cx = txy[0];
  }

  var minI = 0, minDist = Infinity;
  for (var i = 0; i < anchors.length; i++) {
    var anchor = anchors[i];

    var ax = xy[0] + (anchor.x * wh[0]);

     if (ax > cx) {
       // force flow chart only to left
       continue;
     }

    var ay = xy[1] + (anchor.y * wh[1]);
    var d = distance(ax, ay, cx, cy);

    if (d < minDist) {
      minI = i;
      minDist = d;
    }
  }

  return anchors[minI];
};

var getConnectorOverlay = function(location) {
  return ['Custom', {
    create: function() {
      return $('<a href="#"><i class="fa fa-times"></i></a>');
    },
    location: location,
    cssClass: 'network-delete-connection'
  }];
};

var getConnectorOverlays = function() {
  return [getConnectorOverlay(1), getConnectorOverlay(0)];
};

var getContainerNetworkLinksFromElements = function(sourceEl, targetEl) {
  var containerDescriptionLink = sourceEl.getAttribute('data-containerDescriptionLink');
  var networkDescriptionLink = targetEl.getAttribute('data-networkDescriptionLink');

  if (!containerDescriptionLink || !networkDescriptionLink) {
    containerDescriptionLink = targetEl.getAttribute('data-containerDescriptionLink');
    networkDescriptionLink = sourceEl.getAttribute('data-networkDescriptionLink');
  }

  if (!containerDescriptionLink || !networkDescriptionLink) {
    return null;
  }
  return [containerDescriptionLink, networkDescriptionLink];
};

var getContainerNetworkLinksFromEndpoints = function(sourceEndpoint, targetEndpoint) {
  return getContainerNetworkLinksFromElements(sourceEndpoint.getElement(),
                                              targetEndpoint.getElement());
};

var getContainerNetworkLinksFromEventInfo = function(info) {
  return getContainerNetworkLinksFromEndpoints(info.sourceEndpoint, info.targetEndpoint);
};

var getContainerToNetworkLinks = function(jsplumbInstance) {
  var result = {};

  var connections = jsplumbInstance.getConnections();
  for (var i = 0; i < connections.length; i++) {
    var endpoints = connections[i].endpoints;
    if (!endpoints || endpoints.length !== 2) {
      continue;
    }

    var links = getContainerNetworkLinksFromEndpoints(endpoints[0], endpoints[1]);
    var containerDescriptionLink = links[0];
    var networkDescriptionLink = links[1];
    if (!result[containerDescriptionLink]) {
      result[containerDescriptionLink] = [];
    }
    result[containerDescriptionLink].push(networkDescriptionLink);
  }

  return result;
};

var bindToNetworkConnectionEvent = function(jsplumbInstance, eventName, callback) {
  jsplumbInstance.bind(eventName, function(info) {
    var links = getContainerNetworkLinksFromEventInfo(info);
    if (links) {
      callback(links[0], links[1]);
    }
  });
};

var findFreeEndpoints = function(jsplumbInstance, $els) {
  var sourceEndpoints = [];
  $els.sort(function(a, b) {
    var d1 = distance(0, 0, a.offsetLeft, a.offsetTop);
    var d2 = distance(0, 0, b.offsetLeft, b.offsetTop);
    return d1 >= d2;
  }).each((_, el) => {
    var endpoints = jsplumbInstance.getEndpoints(el);
    endpoints.forEach((endpoint) => {
      if (!endpoint.isFull()) {
        sourceEndpoints.push(endpoint);
      }
    });
  });

  return sourceEndpoints;
};

var NetworkConnectorMixin = {
  attached: function() {
    if (!utils.isNetworkingAvailable()) {
      return;
    }
    this.jsplumbInstance = jsPlumb.getInstance({
      Connector: ['Flowchart', {
        cornerRadius: 5
      }],
      PaintStyle: {
        lineWidth: 1,
        strokeStyle: '#333333',
        outlineColor: 'transparent',
        outlineWidth: 10
      },
      Endpoint: ['Dot', { radius: 5 }],
      EndpointStyle: { fillStyle: '#ffa500' },
      ConnectorPaintStyle: {
        lineWidth: 1
      },
      ConnectorHoverPaintStyle: {
        lineWidth: 3
      },
      HoverPaintStyle: {
        lineWidth: 3,
        outlineColor: 'transparent',
        outlineWidth: 10
      },
      Container: $(this.$el).find('.grid-container')[0]
    });

    this.jsplumbInstance['pointer-events'] = 'all';

    var makeDynamicAnchor = this.jsplumbInstance.makeDynamicAnchor;

    var netAnchors = [];
    for (var i = 0; i <= 30; i++) {
        var anchor = [1 / 30 * i, 0.5, 0, -1];
        netAnchors.push(anchor);
    }
    this.jsplumbInstance.makeDynamicAnchor = function() {
        return makeDynamicAnchor(netAnchors, anchorSelector);
    };

    $(this.$el).on('click', '.network-delete-connection', (e) => {
      e.preventDefault();
      e.stopPropagation();

      var overlayId = e.currentTarget._jsPlumb.id;
      var jsplumbInstance = this.jsplumbInstance;
      var connections = jsplumbInstance.getConnections();
      for (var i = 0; i < connections.length; i++) {
        var conn = connections[i];
        var overlay = conn.getOverlay(overlayId);
        if (overlay) {
          jsplumbInstance.detach(conn);
        }
      }
    });

    this.containerEndpointsPerLink = {};
    this.containerNetworksHolder = {};
  },
  methods: {
    prepareContainerEndpoints: function(networksHolder, containerDescriptionLink) {
      if (!utils.isNetworkingAvailable()) {
        return;
      }

      this.containerNetworksHolder[containerDescriptionLink] = networksHolder;
    },
    updateContainerEndpoints(networks, containerDescriptionLink) {
      try {
        var containerEndpoints = $(this.$el)
          .find('[data-containerDescriptionLink="' + containerDescriptionLink + '"]');

        var diff = networks.length - containerEndpoints.length;
        if (diff === 0) {
          return;
        } else if (diff > 0) {
          for (let i = 0; i < diff; i++) {
            var $el = $('<div>', {class: 'container-network-anchor'});
            $(this.containerNetworksHolder[containerDescriptionLink]).append($el);

            var el = $el[0];
            el.setAttribute('data-containerDescriptionLink', containerDescriptionLink);
            this.jsplumbInstance.addEndpoint(el, {
              maxConnections: 1,
              isSource: true,
              isTarget: true,
              anchor: 'BottomCenter',
              endpoint: ['Image', {
                src: 'image-assets/resource-icons/network-small.png',
                cssClass: 'container-link'
              }],
              deleteEndpointsOnDetach: false,
              connectorOverlays: getConnectorOverlays()
            });
          }
        } else {
          var freeEndpoints = findFreeEndpoints(this.jsplumbInstance, containerEndpoints);
          for (let i = 0; i < -diff && i < freeEndpoints.length; i++) {
            this.jsplumbInstance.deleteEndpoint(freeEndpoints[i]);
            $(freeEndpoints[i].getElement()).remove();
          }
        }

        this.containerEndpointsPerLink[containerDescriptionLink] = networks;
      } catch (e) {
        console.error(e);
      }
    },
    addNetworkEndpoint: function(el, networkDescriptionLink) {
      if (!utils.isNetworkingAvailable()) {
        return;
      }

      el.setAttribute('data-networkDescriptionLink', networkDescriptionLink);
      this.jsplumbInstance.makeSource(el, {
        maxConnections: -1,
        anchor: ['Perimeter', {
          shape: 'Rectangle',
          anchorCount: 160
        }],
        endpoint: ['Image', {
          src: 'image-assets/resource-icons/network-link.png',
          cssClass: 'network-link'
        }],
        deleteEndpointsOnDetach: false,
        connectorOverlays: getConnectorOverlays()
      });

      this.jsplumbInstance.makeTarget(el, {
        maxConnections: -1,
        anchor: ['Perimeter', {
          shape: 'Rectangle',
          anchorCount: 160
        }],
        endpoint: ['Image', {src: 'image-assets/resource-icons/network-link.png'}],
        deleteEndpointsOnDetach: false,
        connectorOverlays: getConnectorOverlays()
      });
    },
    removeNetworkEndpoint: function(el) {
      if (!utils.isNetworkingAvailable()) {
        return;
      }

      this.jsplumbInstance.unmakeTarget(el);
      this.jsplumbInstance.unmakeSource(el);
    },
    onLayoutUpdate: function() {
      if (!utils.isNetworkingAvailable()) {
        return;
      }

      this.jsplumbInstance.repaintEverything();
    },
    bindNetworkConnection: function(callback) {
      if (!utils.isNetworkingAvailable()) {
        return;
      }

      bindToNetworkConnectionEvent(this.jsplumbInstance, 'connection', callback);
    },
    bindNetworkDetachConnection: function(callback) {
      if (!utils.isNetworkingAvailable()) {
        return;
      }

      bindToNetworkConnectionEvent(this.jsplumbInstance, 'connectionDetached', callback);
    },
    applyContainerToNetworksLinks: function(containerToNetworksLinks) {
      if (!utils.isNetworkingAvailable()) {
        return;
      }

      var existingLinks = getContainerToNetworkLinks(this.jsplumbInstance);

      var linksToAdd = {};
      var linksToRemove = $.extend({}, existingLinks);

      for (var link in containerToNetworksLinks) {
        if (!containerToNetworksLinks.hasOwnProperty(link)) {
          continue;
        }
        var existingNetworks = existingLinks[link] || [];
        var networks = containerToNetworksLinks[link] || [];

        linksToAdd[link] = networks.filter(x => existingNetworks.indexOf(x) === -1);
        linksToRemove[link] = existingNetworks.filter(x => networks.indexOf(x) === -1);
      }

      this.jsplumbInstance.batch(() => {
        for (var containerToRemove in linksToRemove) {
          if (!linksToRemove.hasOwnProperty(containerToRemove)) {
            continue;
          }

          let networksToRemove = linksToRemove[containerToRemove];
          for (let i = 0; i < networksToRemove.length; i++) {
            let networkToRemove = networksToRemove[i];

            this.jsplumbInstance.getConnections().forEach((con) => {
              var links = getContainerNetworkLinksFromElements(con.source, con.target);
              if (links && links[0] === containerToRemove && links[1] === networkToRemove) {
                this.jsplumbInstance.detach(con, {fireEvent: false});
              }
            });
          }

          var networks = this.containerEndpointsPerLink[containerToRemove];
          this.updateContainerEndpoints(networks, containerToRemove);
        }

        for (var containerToAdd in linksToAdd) {
          if (!linksToAdd.hasOwnProperty(containerToAdd)) {
            continue;
          }
          let $containers = $(this.$el)
            .find('[data-containerDescriptionLink="' + containerToAdd + '"]:visible');

          let networksToAdd = linksToAdd[containerToAdd];
          for (let i = 0; i < networksToAdd.length; i++) {
            var networkToAdd = networksToAdd[i];
            var sourceEndpoints = findFreeEndpoints(this.jsplumbInstance, $containers);
            if (!sourceEndpoints || !sourceEndpoints.length) {
              // no free source endpoint
              continue;
            }

            let $networks = $(this.$el)
              .find('[data-networkDescriptionLink="' + networkToAdd + '"]');

            this.jsplumbInstance.connect({
              sourceEndpoint: sourceEndpoints[0],
              target: $networks[0],
              fireEvent: false
            });
          }
        }
      });
    }
  }
};

export default NetworkConnectorMixin;
