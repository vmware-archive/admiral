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
module.exports = (function () {
    function RadialProgress(parent) {
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
    RadialProgress.prototype.component = function () {
        var _this = this;
        var that = this;
        this._selection.each(function (data) {
            // Select the svg element, if it exists.
            var svg = _this._selection.selectAll('svg').data([data]);
            var enter = svg.enter().append('svg').attr('class', 'radial-progress-radial-svg').append('g');
            _this.measure();
            svg.attr('width', '100%')
                .attr('height', '100%')
                .attr('viewBox', '0, 0, ' + _this._width + ', ' + _this._height);
            var background = enter.append('g').attr('class', 'radial-progress-component')
                .attr('cursor', 'pointer')
                .on('click', onMouseClick);
            _this._arc.endAngle(360 * (Math.PI / 180));
            background.append('rect')
                .attr('class', 'radial-progress-background')
                .attr('width', _this._width)
                .attr('height', _this._height);
            background.append('path')
                .attr('transform', 'translate(' + _this._width / 2 + ',' + _this._width / 2 + ')')
                .attr('d', _this._arc);
            background.append('text')
                .attr('class', 'radial-progress-label')
                .attr('transform', 'translate(' + _this._width / 2 + ',' + (_this._width + _this._fontSize) + ')')
                .text(_this._label);
            svg.select('g')
                .attr('transform', 'translate(' + _this._margin.left + ',' + _this._margin.top + ')');
            _this._arc.endAngle(_this._currentArc);
            enter.append('g').attr('class', 'radial-progress-arcs');
            var path = svg.select('.radial-progress-arcs').selectAll('.radial-progress-arc').data(data);
            path.enter().append('path')
                .attr('class', 'radial-progress-arc')
                .attr('transform', 'translate(' + _this._width / 2 + ',' + _this._width / 2 + ')')
                .attr('d', _this._arc);
            // Another path in case we exceed 100%
            var path2 = svg.select('.radial-progress-arcs').selectAll('.radial-progress-arc2').data(data);
            path2.enter().append('path')
                .attr('class', 'radial-progress-arc2')
                .attr('transform', 'translate(' + _this._width / 2 + ',' + _this._width / 2 + ')')
                .attr('d', _this._arc2);
            enter.append('g').attr('class', 'radial-progress-labels');
            var yStart = _this._width / 2 + _this._fontSize / 3;
            var label = svg.select('.radial-progress-labels').selectAll('.radial-progress-label')
                .data(data);
            label.enter().append('text')
                .attr('class', 'radial-progress-label')
                .attr('y', yStart)
                .attr('x', _this._width / 2)
                .attr('cursor', 'pointer')
                .attr('width', _this._width)
                .style('font-size', _this._fontSize + 'px')
                .on('click', onMouseClick);
            label.text(function () {
                if (_this._majorTitle) {
                    return _this._majorTitle;
                }
                return _this.roundPercentage((_this._value - _this._minValue) / (_this._maxValue - _this._minValue) * 100) + '%';
            });
            var minorLabel = svg.select('.radial-progress-labels')
                .selectAll('.radial-progress-minor-label').data(data);
            minorLabel.enter().append('text')
                .attr('class', 'radial-progress-minor-label')
                .attr('y', yStart + _this._smallLabelPadding)
                .attr('x', _this._width / 2)
                .attr('cursor', 'pointer')
                .attr('width', _this._width)
                .style('font-size', _this._smallFontSize + 'px')
                .on('click', onMouseClick);
            minorLabel.text(function () {
                if (_this._minorTitle) {
                    return _this._minorTitle;
                }
            });
            path.exit().transition().duration(500).attr('x', 1000).remove();
            function labelTween(a) {
                var i = d3.interpolate(that._currentValue, a);
                that._currentValue = i(0);
                return function (t) {
                    that._currentValue = i(t);
                };
            }
            function arcTween(a) {
                var i = d3.interpolate(that._currentArc, a);
                return function (t) {
                    that._currentArc = i(t);
                    return that._arc.endAngle(i(t))();
                };
            }
            function arcTween2(a) {
                var i = d3.interpolate(that._currentArc2, a);
                return function (t) {
                    that._currentArc2 = i(t);
                    return that._arc2.endAngle(i(t))();
                };
            }
            var layout = function () {
                var ratio = (_this._value - _this._minValue) / (_this._maxValue - _this._minValue);
                var endAngle = Math.min(360 * ratio, 360);
                endAngle = endAngle * Math.PI / 180;
                if (ratio <= 1 && _this._currentArc2 > 0) {
                    path2.datum(0);
                    path2.transition().duration(_this._duration)
                        .attrTween('d', arcTween2);
                    path.datum(endAngle);
                    path.transition().delay(_this._duration).duration(_this._duration)
                        .attrTween('d', arcTween);
                }
                else {
                    path.datum(endAngle);
                    path.transition().duration(_this._duration)
                        .attrTween('d', arcTween);
                }
                if (ratio > 1) {
                    path2.datum(Math.min(360 * (ratio - 1), 360) * Math.PI / 180);
                    path2.transition().delay(_this._duration).duration(_this._duration)
                        .attrTween('d', arcTween2);
                }
                if (!_this._majorTitle) {
                    label.datum(_this.roundPercentage(ratio * 100));
                    label.transition().duration(_this._duration)
                        .tween('text', labelTween);
                }
            };
            layout();
        });
        var onMouseClick = function () {
            if (typeof that._mouseClick === 'function') {
                that._mouseClick.call();
            }
        };
    };
    RadialProgress.prototype.roundPercentage = function (p) {
        return Math.round(p * 100) / 100;
    };
    RadialProgress.prototype.measure = function () {
        this._arc.outerRadius(this._width / 2);
        this._arc.innerRadius(this._width / 2 * 0.85);
        this._arc2.outerRadius(this._width / 2 * 0.85);
        this._arc2.innerRadius(this._width / 2 * 0.85 - (this._width / 2 * 0.15));
    };
    RadialProgress.prototype.render = function () {
        this.measure();
        this.component();
        return this;
    };
    ;
    RadialProgress.prototype.value = function (_) {
        this._value = _;
        this._selection.datum([this._value]);
        return this;
    };
    ;
    RadialProgress.prototype.majorTitle = function (_) {
        this._majorTitle = _;
        return this;
    };
    ;
    RadialProgress.prototype.minorTitle = function (_) {
        this._minorTitle = _;
        return this;
    };
    ;
    RadialProgress.prototype.margin = function (_) {
        this._margin = _;
        return this;
    };
    ;
    RadialProgress.prototype.diameter = function (_) {
        this._diameter = _;
        return this;
    };
    ;
    RadialProgress.prototype.minValue = function (_) {
        this._minValue = _;
        return this;
    };
    ;
    RadialProgress.prototype.maxValue = function (_) {
        this._maxValue = _;
        return this;
    };
    ;
    RadialProgress.prototype.label = function (_) {
        this._label = _;
        return this;
    };
    ;
    RadialProgress.prototype.duration = function (_) {
        this._duration = _;
        return this;
    };
    ;
    RadialProgress.prototype.onClick = function (_) {
        this._mouseClick = _;
        return this;
    };
    ;
    return RadialProgress;
}());
//# sourceMappingURL=RadialProgress.js.map