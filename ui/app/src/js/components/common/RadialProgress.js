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

function RadialProgress(parent) {
  var _duration = 1000;
  var _selection;
  var _margin = {
    top: 0,
    right: 0,
    bottom: 30,
    left: 0
  };
  var _width = 120;
  var _height = 150;
  var _diameter;
  var _label = '';
  var _fontSize = 24;
  var _smallFontSize = 12;
  var _majorTitle;
  var _minorTitle;

  var _mouseClick;

  var _value = 0;
  var _minValue = 0;
  var _maxValue = 100;

  var _currentArc = 0;
  var _currentArc2 = 0;
  var _currentValue = 0;

  var _arc = d3.svg.arc()
    .startAngle(0 * (Math.PI / 180)); // Just radians

  var _arc2 = d3.svg.arc()
    .startAngle(0 * (Math.PI / 180))
    .endAngle(0); // Just radians

  _selection = d3.select(parent);

  function component() {
    _selection.each(function(data) {

      // Select the svg element, if it exists.
      var svg = d3.select(this).selectAll('svg').data([data]);

      var enter = svg.enter().append('svg').attr('class', 'radial-progress-radial-svg').append(
        'g');

      measure();

      svg.attr('width', '100%')
        .attr('height', '100%')
        .attr('viewBox', '0, 0, ' + _width + ', ' + _height);

      var background = enter.append('g').attr('class', 'radial-progress-component')
        .attr('cursor', 'pointer')
        .on('click', onMouseClick);

      _arc.endAngle(360 * (Math.PI / 180));

      background.append('rect')
        .attr('class', 'radial-progress-background')
        .attr('width', _width)
        .attr('height', _height);

      background.append('path')
        .attr('transform', 'translate(' + _width / 2 + ',' + _width / 2 + ')')
        .attr('d', _arc);

      background.append('text')
        .attr('class', 'radial-progress-label')
        .attr('transform', 'translate(' + _width / 2 + ',' + (_width + _fontSize) + ')')
        .text(_label);

      svg.select('g')
        .attr('transform', 'translate(' + _margin.left + ',' + _margin.top + ')');

      _arc.endAngle(_currentArc);
      enter.append('g').attr('class', 'radial-progress-arcs');
      var path = svg.select('.radial-progress-arcs').selectAll('.radial-progress-arc').data(
        data);
      path.enter().append('path')
        .attr('class', 'radial-progress-arc')
        .attr('transform', 'translate(' + _width / 2 + ',' + _width / 2 + ')')
        .attr('d', _arc);

      // Another path in case we exceed 100%
      var path2 = svg.select('.radial-progress-arcs').selectAll('.radial-progress-arc2').data(
        data);
      path2.enter().append('path')
        .attr('class', 'radial-progress-arc2')
        .attr('transform', 'translate(' + _width / 2 + ',' + _width / 2 + ')')
        .attr('d', _arc2);

      enter.append('g').attr('class', 'radial-progress-labels');

      var yStart = _width / 2 + _fontSize / 3;

      var label = svg.select('.radial-progress-labels').selectAll('.radial-progress-label')
        .data(data);
      label.enter().append('text')
        .attr('class', 'radial-progress-label')
        .attr('y', yStart)
        .attr('x', _width / 2)
        .attr('cursor', 'pointer')
        .attr('width', _width)
        .style('font-size', _fontSize + 'px')
        .on('click', onMouseClick);

      label.text(function() {
        if (_majorTitle) {
          return _majorTitle;
        }

        return roundPercentage((_value - _minValue) / (_maxValue - _minValue) * 100) + '%';
      });

      var minorLabel = svg.select('.radial-progress-labels')
        .selectAll('.radial-progress-minor-label').data(data);

      minorLabel.enter().append('text')
        .attr('class', 'radial-progress-minor-label')
        .attr('y', yStart + _smallFontSize)
        .attr('x', _width / 2)
        .attr('cursor', 'pointer')
        .attr('width', _width)
        .style('font-size', _smallFontSize + 'px')
        .on('click', onMouseClick);

      minorLabel.text(function() {
        if (_minorTitle) {
          return _minorTitle;
        }
      });

      path.exit().transition().duration(500).attr('x', 1000).remove();

      layout();

      function layout() {

        var ratio = (_value - _minValue) / (_maxValue - _minValue);
        var endAngle = Math.min(360 * ratio, 360);
        endAngle = endAngle * Math.PI / 180;

        if (ratio <= 1 && _currentArc2 > 0) {
          path2.datum(0);
          path2.transition().duration(_duration)
            .attrTween('d', arcTween2);

          path.datum(endAngle);
          path.transition().delay(_duration).duration(_duration)
            .attrTween('d', arcTween);
        } else {
          path.datum(endAngle);
          path.transition().duration(_duration)
            .attrTween('d', arcTween);
        }

        if (ratio > 1) {
          path2.datum(Math.min(360 * (ratio - 1), 360) * Math.PI / 180);
          path2.transition().delay(_duration).duration(_duration)
            .attrTween('d', arcTween2);
        }

        if (!_majorTitle) {
          label.datum(roundPercentage(ratio * 100));
          label.transition().duration(_duration)
            .tween('text', labelTween);
        }

      }
    });

    function onMouseClick() {
      if (typeof _mouseClick === 'function') {
        _mouseClick.call();
      }
    }
  }

  function labelTween(a) {
    var i = d3.interpolate(_currentValue, a);
    _currentValue = i(0);

    return function(t) {
      _currentValue = i(t);
      this.textContent = roundPercentage(i(t)) + '%';
    };
  }

  function arcTween(a) {
    var i = d3.interpolate(_currentArc, a);

    return function(t) {
      _currentArc = i(t);
      return _arc.endAngle(i(t))();
    };
  }

  function arcTween2(a) {
    var i = d3.interpolate(_currentArc2, a);

    return function(t) {
      _currentArc2 = i(t);
      return _arc2.endAngle(i(t))();
    };
  }

  function roundPercentage(p) {
    return Math.round(p * 100) / 100;
  }

  function measure() {
    _arc.outerRadius(_width / 2);
    _arc.innerRadius(_width / 2 * 0.85);
    _arc2.outerRadius(_width / 2 * 0.85);
    _arc2.innerRadius(_width / 2 * 0.85 - (_width / 2 * 0.15));
  }

  component.render = function() {
    measure();
    component();
    return component;
  };

  component.value = function(_) {
    if (!arguments.length) {
      return _value;
    }

    _value = [_];
    _selection.datum([_value]);
    return component;
  };

  component.majorTitle = function(_) {
    if (!arguments.length) {
      return _majorTitle;
    }

    _majorTitle = _;
    return component;
  };

  component.minorTitle = function(_) {
    if (!arguments.length) {
      return _minorTitle;
    }

    _minorTitle = _;
    return component;
  };

  component.margin = function(_) {
    if (!arguments.length) {
      return _margin;
    }

    _margin = _;
    return component;
  };

  component.diameter = function(_) {
    if (!arguments.length) {
      return _diameter;
    }

    _diameter = _;
    return component;
  };

  component.minValue = function(_) {
    if (!arguments.length) {
      return _minValue;
    }

    _minValue = _;
    return component;
  };

  component.maxValue = function(_) {
    if (!arguments.length) {
      return _maxValue;
    }

    _maxValue = _;
    return component;
  };

  component.label = function(_) {
    if (!arguments.length) {
      return _label;
    }

    _label = _;
    return component;
  };

  component._duration = function(_) {
    if (!arguments.length) {
      return _duration;
    }

    _duration = _;
    return component;
  };

  component.onClick = function(_) {
    if (!arguments.length) {
      return _mouseClick;
    }

    _mouseClick = _;
    return component;
  };

  return component;
}

export default RadialProgress;
