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

var utils = require('../../core/formatUtils');
var d3 = require('d3');

const ARROW_UP = '\uf062';
const ARROW_DOWN = '\uf063';

const ARROW_UP_SVG = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');
ARROW_UP_SVG.setAttribute('class', 'arrow');
ARROW_UP_SVG.textContent = ARROW_UP;

const ARROW_DOWN_SVG = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');
ARROW_DOWN_SVG.setAttribute('class', 'arrow');
ARROW_DOWN_SVG.textContent = ARROW_DOWN;

const width = 240;
const halfWidth = width / 2;
const height = 150;
const fontSize = 18;
const smallFontSize = 12;
const duration = 1000;

const labelHolderWidth = 120;
const labelHolderHeight = 150;
const labelHolderY = labelHolderWidth + 24;

module.exports = class NetworkTrafficVisualization {
  constructor(parent, i18n) {
    var labelHolder = d3.select(parent).append('svg')
      .attr('width', '100%')
      .attr('height', '100%')
      .attr('viewBox', '0, 0, ' + labelHolderWidth + ', ' + labelHolderHeight);

    labelHolder.append('g').attr('class', 'network-traffic-labels')
      .append('text')
      .attr('class', 'radial-progress-label')
      .attr('x', labelHolderWidth / 2)
      .attr('y', labelHolderY)
      .text(i18n.t('app.container.details.network'));

    this.svg = d3.select(parent).append('svg')
      .attr('width', '100%')
      .attr('height', '100%')
      .attr('viewBox', '0, 0, ' + width + ', ' + height);

    var labels = this.svg.append('g').attr('class', 'network-traffic-labels');

    configureIOLabels(labels, 'network-traffic-received-label',
                      i18n.t('app.container.details.networkTrafficReceived'), true);

    var separator = labels.append('g');
    separator.append('text')
      .attr('y', height / 2 - fontSize)
      .attr('x', halfWidth)
      .style('font-size', fontSize + 'px')
      .text('/');

    configureIOLabels(labels, 'network-traffic-sent-label',
                      i18n.t('app.container.details.networkTrafficSent'), false);
  }

  setData(receivedBytes, sentBytes) {
    var currentReceivedBytes = this.receivedBytes || 0;
    var currentSentBytes = this.sentBytes || 0;

    if (currentReceivedBytes !== receivedBytes) {
      var receivedLabel = this.svg.select('.network-traffic-labels')
      .select('.network-traffic-received-label');

      receivedLabel.datum(receivedBytes);
      receivedLabel.transition().duration(duration).tween('text', function(a) {
        var i = d3.interpolateNumber(currentReceivedBytes, a);
        return function(t) {
          this.innerHTML = '';

          var text = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');
          text.textContent = ' ' + utils.formatBytes(i(t));
          this.appendChild(ARROW_DOWN_SVG);
          this.appendChild(text);
        };
      });
    }

    if (currentSentBytes !== sentBytes) {
      var sentLabel = this.svg.select('.network-traffic-labels')
      .select('.network-traffic-sent-label');

      sentLabel.datum(sentBytes);
      sentLabel.transition().duration(duration).tween('text', function(a) {
        var i = d3.interpolateNumber(currentSentBytes, a);
        return function(t) {
          this.innerHTML = '';
          var text = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');
          text.textContent = utils.formatBytes(i(t)) + ' ';
          this.appendChild(text);
          this.appendChild(ARROW_UP_SVG);
        };
      });
    }

    this.receivedBytes = receivedBytes;
    this.sentBytes = sentBytes;
  }

  reset(label) {
    this.receivedBytes = null;
    this.sentBytes = null;

    var receivedLabel = this.svg.select('.network-traffic-labels')
    .select('.network-traffic-received-label');

    receivedLabel.text(label);

    var sentLabel = this.svg.select('.network-traffic-labels')
    .select('.network-traffic-sent-label');

    sentLabel.text(label);
  }
}

function configureIOLabels(labels, className, underneathText, isLeft) {
  var x = isLeft ? halfWidth / 2 : halfWidth + halfWidth / 2;

  var ioLabels = labels.append('g');
  ioLabels.append('text')
   .attr('class', className)
   .attr('y', height / 2 - fontSize)
   .attr('x', x)
   .style('font-size', fontSize + 'px');
  ioLabels.append('text')
   .attr('class', 'network-traffic-minor-label')
   .attr('y', height / 2)
   .attr('x', x)
   .style('font-size', smallFontSize + 'px')
   .text(underneathText);
}
