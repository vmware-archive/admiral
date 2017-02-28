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

// Radial progress d3 component by
// http://www.brightpointinc.com/clients/brightpointinc.com/library/radialProgress/download.html
// Added some minor modification to support size scaling and multiple level of titles in the center.

var d3 = require('d3');

module.exports = class RadialProgress {
  constructor(parent) {
      this._duration = 1000;
      this._selection;
      this._margin = {
          top: 0,
          right: 0,
          bottom: 30,
          left: 0
      };
      this._width = 120;
      this._height = 150;
      this._diameter;
      this._label = '';
      this._fontSize = 24;
      this._smallFontSize = 12;
      this._smallLabelPadding = 17;
      this._majorTitle;
      this._minorTitle;
      this._mouseClick;
      this._value = 0;
      this._minValue = 0;
      this._maxValue = 100;
      this._currentArc = 0;
      this._currentArc2 = 0;
      this._currentValue = 0;

      this._arc = d3.svg.arc()
        .startAngle(0 * (Math.PI / 180)); // Just radians

      this._arc2 = d3.svg.arc()
        .startAngle(0 * (Math.PI / 180))
        .endAngle(0); // Just radians

      this._selection = d3.select(parent);
  }


  component() {
    var that = this;

    this._selection.each((data) => {

      // Select the svg element, if it exists.
      var svg = this._selection.selectAll('svg').data([data]);

      var enter = svg.enter().append('svg').attr('class', 'radial-progress-radial-svg').append(
        'g');

      this.measure();

      svg.attr('width', '100%')
        .attr('height', '100%')
        .attr('viewBox', '0, 0, ' + this._width + ', ' + this._height);

      var background = enter.append('g').attr('class', 'radial-progress-component')
        .attr('cursor', 'pointer')
        .on('click', onMouseClick);

      this._arc.endAngle(360 * (Math.PI / 180));

      background.append('rect')
        .attr('class', 'radial-progress-background')
        .attr('width', this._width)
        .attr('height', this._height);

      background.append('path')
        .attr('transform', 'translate(' + this._width / 2 + ',' + this._width / 2 + ')')
        .attr('d', this._arc);

      background.append('text')
        .attr('class', 'radial-progress-label')
        .attr('transform', 'translate(' + this._width / 2 + ',' + (this._width + this._fontSize) + ')')
        .text(this._label);

      svg.select('g')
        .attr('transform', 'translate(' + this._margin.left + ',' + this._margin.top + ')');

      this._arc.endAngle(this._currentArc);
      enter.append('g').attr('class', 'radial-progress-arcs');
      var path = svg.select('.radial-progress-arcs').selectAll('.radial-progress-arc').data(
        data);
      path.enter().append('path')
        .attr('class', 'radial-progress-arc')
        .attr('transform', 'translate(' + this._width / 2 + ',' + this._width / 2 + ')')
        .attr('d', this._arc);

      // Another path in case we exceed 100%
      var path2 = svg.select('.radial-progress-arcs').selectAll('.radial-progress-arc2').data(
        data);
      path2.enter().append('path')
        .attr('class', 'radial-progress-arc2')
        .attr('transform', 'translate(' + this._width / 2 + ',' + this._width / 2 + ')')
        .attr('d', this._arc2);

      enter.append('g').attr('class', 'radial-progress-labels');

      var yStart = this._width / 2 + this._fontSize / 3;

      var label = svg.select('.radial-progress-labels').selectAll('.radial-progress-label')
        .data(data);
      label.enter().append('text')
        .attr('class', 'radial-progress-label')
        .attr('y', yStart)
        .attr('x', this._width / 2)
        .attr('cursor', 'pointer')
        .attr('width', this._width)
        .style('font-size', this._fontSize + 'px')
        .on('click', onMouseClick);

      label.text(() => {
        if (this._majorTitle) {
          return this._majorTitle;
        }

        return this.roundPercentage((this._value - this._minValue) / (this._maxValue - this._minValue) * 100) + '%';
      });

      var minorLabel = svg.select('.radial-progress-labels')
        .selectAll('.radial-progress-minor-label').data(data);

      minorLabel.enter().append('text')
        .attr('class', 'radial-progress-minor-label')
        .attr('y', yStart + this._smallLabelPadding)
        .attr('x', this._width / 2)
        .attr('cursor', 'pointer')
        .attr('width', this._width)
        .style('font-size', this._smallFontSize + 'px')
        .on('click', onMouseClick);

      minorLabel.text(() => {
        if (this._minorTitle) {
          return this._minorTitle;
        }
      });

      path.exit().transition().duration(500).attr('x', 1000).remove();

      function labelTween(a) {
        var i = d3.interpolate(that._currentValue, a);
        that._currentValue = i(0);
        return (t) => {
          that._currentValue = i(t);
        };
      }

      function arcTween(a) {
        var i = d3.interpolate(that._currentArc, a);
        return (t) => {
          that._currentArc = i(t);
          return that._arc.endAngle(i(t))();
        };
      }

      function arcTween2(a) {
        var i = d3.interpolate(that._currentArc2, a);
        return (t) => {
          that._currentArc2 = i(t);
          return that._arc2.endAngle(i(t))();
        };
      }

      let layout = () => {
        var ratio = (this._value - this._minValue) / (this._maxValue - this._minValue);
        var endAngle = Math.min(360 * ratio, 360);
        endAngle = endAngle * Math.PI / 180;

        if (ratio <= 1 && this._currentArc2 > 0) {
          path2.datum(0);
          path2.transition().duration(this._duration)
            .attrTween('d', arcTween2);

          path.datum(endAngle);
          path.transition().delay(this._duration).duration(this._duration)
            .attrTween('d', arcTween);
        } else {
          path.datum(endAngle);
          path.transition().duration(this._duration)
            .attrTween('d', arcTween);
        }

        if (ratio > 1) {
          path2.datum(Math.min(360 * (ratio - 1), 360) * Math.PI / 180);
          path2.transition().delay(this._duration).duration(this._duration)
            .attrTween('d', arcTween2);
        }

        if (!this._majorTitle) {
          label.datum(this.roundPercentage(ratio * 100));
          label.transition().duration(this._duration)
            .tween('text', labelTween);
        }

      }

      layout();
    });

    let onMouseClick = () => {
      if (typeof that._mouseClick === 'function') {
        that._mouseClick.call();
      }
    }
  }

  roundPercentage(p) {
    return Math.round(p * 100) / 100;
  }

  measure() {
    this._arc.outerRadius(this._width / 2);
    this._arc.innerRadius(this._width / 2 * 0.85);
    this._arc2.outerRadius(this._width / 2 * 0.85);
    this._arc2.innerRadius(this._width / 2 * 0.85 - (this._width / 2 * 0.15));
  }

  render() {
    this.measure();
    this.component();
    return this;
  };

  value(_) {
    this._value = _;
    this._selection.datum([this._value]);
    return this;
  };

  majorTitle(_) {
    this._majorTitle = _;
    return this;
  };

  minorTitle(_) {
    this._minorTitle = _;
    return this;
  };

  margin(_) {
    this._margin = _;
    return this;
  };

  diameter(_) {
    this._diameter = _;
    return this;
  };

  minValue(_) {
    this._minValue = _;
    return this;
  };

  maxValue(_) {
    this._maxValue = _;
    return this;
  };

  label(_) {
    this._label = _;
    return this;
  };

  duration(_) {
    this._duration = _;
    return this;
  };

  onClick(_) {
    this._mouseClick = _;
    return this;
  };
}