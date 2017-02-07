/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { TemplateActions } from 'actions/Actions';
import constants from 'core/constants';

const TEMP_VOLUME_PATH = '/container/project/path';

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
    cssClass: 'resource-delete-connection'
  }];
};

var getConnectorOverlays = function(isEditable, isVolume, volumeContainerPath) {
  var connOverlays = [getConnectorOverlay(1), getConnectorOverlay(0)];
  if (isVolume) {
    return connOverlays.concat(
                          getVolumeConnectionOverlays(isEditable, isVolume, volumeContainerPath));
  }

  return connOverlays;
};

var getVolumeConnectionOverlays = function(isEditable, isVolume, volumeContainerPath) {
  let connOverlays = [];

  if (isVolume) {

    if (isEditable) {
      let editLinkValueOverlay = ['Custom', {
        create: function() {
          var html = '<div style="display:none;">' +
            '<label class="control-label">' +
            i18n.t('app.template.details.volume.editContainerPath') + '</label> ' +
            '<input class="volume-container-path" type="text" value="'
            + volumeContainerPath + '">' +
            '<a href="#" class="btn btn-circle volume-container-path-confirm">' +
            '<i class="fa fa-check"></i></a>' +
            '<a href="#" class="btn btn-circle volume-container-path-cancel">' +
            '<i class="fa fa-times"></i></a></div>';
          return $(html);
        },
        location: 0.9,
        id: 'volume-link-value-edit',
        cssClass: 'resource-link-value-edit',
        events: {
          click: function(overlayComponent, e) {
            let $targetEl = $(e.target);

            let isConfirm = $targetEl.hasClass('volume-container-path-confirm')
                              || $targetEl.parent().hasClass('volume-container-path-confirm');
            let isCancel = $targetEl.hasClass('volume-container-path-cancel')
                              || $targetEl.parent().hasClass('volume-container-path-cancel');

            if (isConfirm) {
              e.preventDefault();
              e.stopPropagation();

              overlayComponent.hide();

              let containerPath = $(overlayComponent.getElement())
                                                            .find('.volume-container-path').val();

              let overlayVolumeLinkValue =
                overlayComponent.component.getOverlay('volume-link-value');
              overlayVolumeLinkValue.show();
              overlayVolumeLinkValue.setLabel(
                '<div class="volume-container-path-value editable truncateText">' +
                containerPath + '</div>' +
                '<span class="edit-volume-container-path"><i class="fa fa-pencil"></i></span>');

              // Save the value for container path
              let endpoints = overlayComponent.component.endpoints;

              if (endpoints && endpoints.length === 2) {
                let dataLinks = getContainerResourceLinksFromEndpoints(endpoints[0], endpoints[1],
                                                        constants.RESOURCE_CONNECTION_TYPE.VOLUME);
                if (dataLinks) {
                  let volumeDescriptionLink = dataLinks[1];
                  let containerDescriptionLink = dataLinks[0];

                  TemplateActions.editAttachedVolume(containerDescriptionLink,
                    volumeDescriptionLink, containerPath);
                }
              }

            } else if (isCancel) {
              e.preventDefault();
              e.stopPropagation();

              overlayComponent.hide();
              overlayComponent.component.getOverlay('volume-link-value').show();
            }
          }
        }
      }];

      connOverlays.push(editLinkValueOverlay);
    }

    let containerVolumePathHtml = '<div class="volume-container-path-value';
    if (isEditable) {
      containerVolumePathHtml += ' editable';
    }
    containerVolumePathHtml += ' truncateText">' + volumeContainerPath + '</div>';
    if (isEditable) {
      containerVolumePathHtml += '<span class="edit-volume-container-path">' +
      '<i class="fa fa-pencil"></i></span>';
    }

    let labelOverlay = ['Label', {
      label: containerVolumePathHtml,
      location: 0.5,
      id: 'volume-link-value',
      cssClass: 'resource-link-value',
      events: {
        click: function(overlayComponent, e) {
          e.preventDefault();
          e.stopPropagation();

          if (isEditable) {
            overlayComponent.hide();
            overlayComponent.component.getOverlay('volume-link-value-edit').show();
          }
        }
      }
    }];

    connOverlays.push(labelOverlay);
  }

  return connOverlays;
};

var getContainerResourceLinksFromElements = function(sourceEl, targetEl, resourceType) {
  var containerDescriptionLink = sourceEl.getAttribute('data-containerDescriptionLink');
  var resourceDescriptionLink = targetEl.getAttribute('data-resourceDescriptionLink');

  if (!containerDescriptionLink || !resourceDescriptionLink) {
    containerDescriptionLink = targetEl.getAttribute('data-containerDescriptionLink');
    resourceDescriptionLink = sourceEl.getAttribute('data-resourceDescriptionLink');
  }

  if (!containerDescriptionLink || !resourceDescriptionLink) {
    return null;
  }

  if (resourceType && resourceDescriptionLink.indexOf(resourceType) < 0) {
    return null;
  }

  return [containerDescriptionLink, resourceDescriptionLink];
};

var getContainerResourceLinksFromEndpoints = function(sourceEndpoint, targetEndpoint,
                                                      resourceType) {
  return getContainerResourceLinksFromElements(sourceEndpoint.getElement(),
                                               targetEndpoint.getElement(),
                                               resourceType);
};

var getContainerResourceLinksFromEventInfo = function(info, resourceType) {
  return getContainerResourceLinksFromEndpoints(info.sourceEndpoint, info.targetEndpoint,
                                                resourceType);
};

var getContainerToResourceLinks = function(jsplumbInstance, resourceType) {
  var result = {};

  var connections = jsplumbInstance.getConnections();
  for (var i = 0; i < connections.length; i++) {
    var endpoints = connections[i].endpoints;

    if (!endpoints || endpoints.length !== 2) {
      continue;
    }

    var links = getContainerResourceLinksFromEndpoints(endpoints[0], endpoints[1], resourceType);
    if (links) {
      var resourceDescriptionLink = links[1];
      var containerDescriptionLink = links[0];

      if (!result[containerDescriptionLink]) {
        result[containerDescriptionLink] = [];
      }

      result[containerDescriptionLink].push(resourceDescriptionLink);
    }
  }

  return result;
};

var bindToResourceConnectionEvent = function(jsplumbInstance, eventName, callback, resourceType,
                                             isConnect, isEditable) {

  jsplumbInstance.bind(eventName, function(info) {
    var links = getContainerResourceLinksFromEventInfo(info);

    if (links && !this.ignoreSingleConnectionEvents) {

      if (links[1].indexOf(resourceType) > 0) {

        if (isConnect && resourceType === constants.RESOURCE_CONNECTION_TYPE.VOLUME) {
          let conn = info.connection;
          let volumeConnectionOverlays = getVolumeConnectionOverlays(isEditable, true,
                                                                      TEMP_VOLUME_PATH);
          volumeConnectionOverlays.forEach((volumeOverlay) => {
            conn.addOverlay(volumeOverlay);
          });
        }

        callback(links[0], links[1]);
      }
    }
  });
};

var bindToResourceConnectionMovedEvent = function(jsplumbInstance, callback, resourceType) {

  jsplumbInstance.bind('connectionMoved', function(info) {

    // Fix for JSPlumb invoking a synchronous "connection" event right after "connectionMoved"
    this.ignoreSingleConnectionEvents = true;
    setTimeout(() => {
      this.ignoreSingleConnectionEvents = false;
    }, 0);

    var oldLinks = getContainerResourceLinksFromEndpoints(info.originalSourceEndpoint,
                                                          info.originalTargetEndpoint,
                                                          resourceType);
    var newLinks = getContainerResourceLinksFromEndpoints(info.newSourceEndpoint,
                                                          info.newTargetEndpoint,
                                                          resourceType);
    if (oldLinks && newLinks) {
      callback(oldLinks[0], oldLinks[1], newLinks[0], newLinks[1]);
    }
  });
};

var findFreeEndpoints = function(jsplumbInstance, $els) {
  var sourceEndpoints = [];

  $els.sort(function(a, b) {
    var d1 = distance(0, 0, a.offsetLeft, a.offsetTop);
    var d2 = distance(0, 0, b.offsetLeft, b.offsetTop);

    return d1 >= d2;

  }).each((_, $el) => {
    var endpoints = jsplumbInstance.getEndpoints($el);
    endpoints.forEach((endpoint) => {
      if (!endpoint.isFull()) {
        sourceEndpoints.push(endpoint);
      }
    });
  });

  return sourceEndpoints;
};

var jsplumbOverrides = function(jsplumbInstance) {
  var makeDynamicAnchor = jsplumbInstance.makeDynamicAnchor;

  var netAnchors = [];
  for (var i = 0; i <= 30; i++) {
    var anchor = [1 / 30 * i, 0.5, 0, -1];
    netAnchors.push(anchor);
  }

  jsplumbInstance.makeDynamicAnchor = function() {
    return makeDynamicAnchor(netAnchors, anchorSelector);
  };

  // override getOffset to respect css transforms
  jsplumbInstance.getOffset = function(el, relativeToRoot, container) {
    el = jsPlumb.getElement(el);
    var elRect = el.getBoundingClientRect();

    if (relativeToRoot) {

      return {
        left: elRect.left,
        top: elRect.top
      };
    } else {
      container = container || this.getContainer();
      var containerRect = container.getBoundingClientRect();
      var scrollLeft = container.scrollLeft || 0;
      var scrollTop = container.scrollTop || 0;

      return {
        left: elRect.left - containerRect.left + scrollLeft,
        top: elRect.top - containerRect.top + scrollTop
      };
    }
  };
};

var ConnectorMixin = {
  attached: function() {

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

    this.jsplumbInstance.bind('beforeDrop', (params) => {
      var sourceEndpoints = this.jsplumbInstance.getEndpoints(params.sourceId) || [];
      var targetEndpoints = this.jsplumbInstance.getEndpoints(params.targetId) || [];

      if (sourceEndpoints.length === 0 || targetEndpoints.length === 0) {
        return false;
      }

      var links = getContainerResourceLinksFromEndpoints(sourceEndpoints[0], targetEndpoints[0]);

      return !!links;
    });

    jsplumbOverrides(this.jsplumbInstance);

    $(this.$el).on('click', '.resource-delete-connection', (e) => {
      e.preventDefault();
      e.stopPropagation();

      if (this.readOnly) {
        return;
      }

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

    this.containerEndpoints = {};
    this.containerResourcesHolder = {};
    this.containerVolumePaths = {};
  },
  methods: {
    prepareContainerEndpoints: function(resourcesHolder, containerDescriptionLink) {
      this.containerResourcesHolder[containerDescriptionLink] = resourcesHolder;
    },
    setResourcesReadOnly: function(value) {
      this.readOnly = value;
    },
    updateContainerEndpoints(resources, containerDescriptionLink, resourceType,
                             containerVolumePaths) {
      try {
        var $containerEndpointEls = $(this.$el)
          .find('[data-containerDescriptionLink="' + containerDescriptionLink
                  + '"][data-resourceType="' + resourceType + '"]');

        let numResources = resources ? resources.length : 0;

        var diff = numResources - $containerEndpointEls.length;
        if (diff === 0) {
          // No changes
          return;
        } else if (diff > 0) {
          // Add endpoints
          for (let i = 0; i < diff; i++) {
            var $el = $('<div>', {class: 'container-resource-anchor'});
            $(this.containerResourcesHolder[containerDescriptionLink]).append($el);

            var el = $el[0];
            el.setAttribute('data-containerDescriptionLink', containerDescriptionLink);
            el.setAttribute('data-resourceType', resourceType);

            this.jsplumbInstance.addEndpoint(el, {
              maxConnections: 1,
              isSource: true,
              isTarget: true,
              enabled: !this.readOnly,
              anchor: 'BottomCenter',
              endpoint: ['Image', {
                src: 'image-assets/resource-icons/' + resourceType + '-small.png',
                cssClass: 'container-link type-' + resourceType
              }],
              deleteEndpointsOnDetach: false,
              connectorOverlays: !this.readOnly ? getConnectorOverlays() : null
            });
          }
        } else {
          // Delete endpoint
          var freeEndpoints = findFreeEndpoints(this.jsplumbInstance, $containerEndpointEls);

          for (let i = 0; i < -diff && i < freeEndpoints.length; i++) {
            this.jsplumbInstance.deleteEndpoint(freeEndpoints[i]);

            $(freeEndpoints[i].getElement()).remove();
          }
        }

        let rt = {};
        rt[resourceType] = resources;
        this.containerEndpoints[containerDescriptionLink] = rt;
        this.containerVolumePaths[containerDescriptionLink] = containerVolumePaths;

      } catch (e) {
        // Error
        console.error(e);
      }
    },
    addResourceEndpoint: function(el, resourceDescriptionLink, resourceType) {
      el.setAttribute('data-resourceDescriptionLink', resourceDescriptionLink);
      el.setAttribute('data-resourceType', resourceType);

      this.jsplumbInstance.makeSource(el, {
        maxConnections: -1,
        anchor: ['Perimeter', {
          shape: 'Rectangle',
          anchorCount: 160
        }],
        endpoint: ['Image', {
          src: 'image-assets/resource-icons/resource-link.png',
          cssClass: 'resource-link type-' + resourceType
        }],
        enabled: !this.readOnly,
        deleteEndpointsOnDetach: false,
        connectorOverlays: getConnectorOverlays()
      });

      this.jsplumbInstance.makeTarget(el, {
        maxConnections: -1,
        anchor: ['Perimeter', {
          shape: 'Rectangle',
          anchorCount: 160
        }],
        endpoint: ['Image', {src: 'image-assets/resource-icons/resource-link.png'}],
        enabled: !this.readOnly,
        deleteEndpointsOnDetach: false,
        connectorOverlays: getConnectorOverlays()
      });
    },
    removeResourceEndpoint: function(el) {
      this.jsplumbInstance.unmakeTarget(el);
      this.jsplumbInstance.unmakeSource(el);
    },
    onLayoutUpdate: function() {
      this.jsplumbInstance.repaintEverything();
    },
    bindResourceConnection: function(resourceType, callback) {
      bindToResourceConnectionEvent(this.jsplumbInstance, 'connection', callback, resourceType,
        true, !this.readOnly);
    },
    bindResourceDetachConnection: function(resourceType, callback) {
      bindToResourceConnectionEvent(this.jsplumbInstance, 'connectionDetached', callback,
                                    resourceType, false, !this.readOnly);
    },
    bindResourceAttachDetachConnection: function(resourceType, callback) {
      bindToResourceConnectionMovedEvent(this.jsplumbInstance, callback, resourceType);
    },
    applyContainerToResourcesLinks: function(containerToResourcesLinks, resourceType) {
      var existingLinks = getContainerToResourceLinks(this.jsplumbInstance, resourceType);

      var linksToAdd = {};
      var linksToRemove = $.extend({}, existingLinks);

      for (var link in containerToResourcesLinks) {
        if (!containerToResourcesLinks.hasOwnProperty(link)) {
          continue;
        }
        var existingResources = existingLinks[link] || [];
        var resources = containerToResourcesLinks[link] || [];

        linksToAdd[link] = resources.filter(x => existingResources.indexOf(x) === -1);
        linksToRemove[link] = existingResources.filter(x => resources.indexOf(x) === -1);
      }

      this.jsplumbInstance.batch(() => {
        // remove connections
        for (var containerToRemove in linksToRemove) {
          if (!linksToRemove.hasOwnProperty(containerToRemove)) {
            continue;
          }

          let resourcesToRemove = linksToRemove[containerToRemove];
          for (let i = 0; i < resourcesToRemove.length; i++) {
            let resourceToRemove = resourcesToRemove[i];

            this.jsplumbInstance.getConnections().forEach((con) => {
              var links = getContainerResourceLinksFromElements(con.source, con.target,
                                                                resourceType);
              if (links && links[0] === containerToRemove && links[1] === resourceToRemove) {
                this.jsplumbInstance.detach(con, {fireEvent: false});
              }
            });
          }

          if (resourcesToRemove && resourcesToRemove.length > 0) {
            let endpointsPerResType = this.containerEndpoints[containerToRemove];
            let resources = endpointsPerResType && endpointsPerResType[resourceType];

            this.updateContainerEndpoints(resources, containerToRemove, resourceType);
          }
        }

        let isVolume = resourceType === constants.RESOURCE_CONNECTION_TYPE.VOLUME;

        // add connections
        for (var containerToAdd in linksToAdd) {
          if (!linksToAdd.hasOwnProperty(containerToAdd)) {
            continue;
          }

          let $containerEndpointEls = $(this.$el)
            .find('[data-containerDescriptionLink="' + containerToAdd
                    + '"][data-resourceType="' + resourceType + '"]' + ':visible');

          let resourcesToAdd = linksToAdd[containerToAdd];
          for (let i = 0; i < resourcesToAdd.length; i++) {
            var resourceToAdd = resourcesToAdd[i];

            var sourceEndpoints = findFreeEndpoints(this.jsplumbInstance, $containerEndpointEls);
            if (!sourceEndpoints || !sourceEndpoints.length) {
              // no free source endpoint
              continue;
            }

            let $resourceEndpointEl = $(this.$el)
                               .find('[data-resourceDescriptionLink="' + resourceToAdd + '"]');

            let volumeContainerPath = isVolume ?
              this.containerVolumePaths[containerToAdd]
              && this.containerVolumePaths[containerToAdd][resourceToAdd] : '';

            let connOverlays = getVolumeConnectionOverlays.call(this, !this.readOnly, isVolume,
                                                                volumeContainerPath);

            this.jsplumbInstance.connect({
              sourceEndpoint: sourceEndpoints[0],
              target: $resourceEndpointEl[0],
              fireEvent: false,
              overlays: connOverlays
            });
          }
        }
      });
    }
  }
};

export default ConnectorMixin;
