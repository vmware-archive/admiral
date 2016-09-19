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

const RADIUS = 40;

var positionElements = function(numOfItems, canvasWidth, canvasHeight, radius) {
  var positions = [];
  var angle = - Math.PI / 2;
  var step = (2 * Math.PI) / numOfItems;

  if (numOfItems === 1) {
    positions.push({
      x: canvasWidth / 2 - radius,
      y: canvasHeight / 2 - radius
    });
    numOfItems--;
  } else if (numOfItems === 2) {
    angle = Math.PI;
  }

  var canvasRadius = canvasWidth / 4;
  for (var i = 0; i < numOfItems; i ++) {
    var x = Math.round(canvasWidth / 2 + canvasRadius * Math.cos(angle) - radius);
    var y = Math.round(canvasHeight / 2 + canvasRadius * Math.sin(angle) - radius);
    positions.push({
      x: x,
      y: y
    });
    angle += step;
  }

  return positions;
};

//var positionElements = function(numOfItems, canvasWidth, canvasHeight, radius) {
//  var colsPerRow = [];
//  var rows = 1;
//  var maxColumns = numOfItems;
//
//  if (numOfItems > 2) {
//    var itemsSqrt = Math.sqrt(numOfItems);
//    rows = Math.ceil(itemsSqrt);
//    maxColumns = rows;
//    var centerRow = (rows - 1) / 2;
//    var prevRow;
//    var nextRow;
//    if (centerRow === Math.floor(centerRow)) {
//      prevRow = centerRow - 1;
//      nextRow = centerRow + 1;
//      colsPerRow[centerRow] = maxColumns;
//      numOfItems -= maxColumns;
//    } else {
//      prevRow = Math.floor(centerRow);
//      nextRow = Math.ceil(centerRow);
//    }
//    var rowIndex = 0;
//    while (numOfItems > 0) {
//      var availableColumns = Math.min(numOfItems, maxColumns * 2);
//      colsPerRow[prevRow - rowIndex] = Math.ceil(availableColumns / 2);
//      colsPerRow[nextRow + rowIndex] = Math.floor(availableColumns / 2);
//
//      numOfItems -= availableColumns;
//      rowIndex++;
//    }
//  } else {
//    colsPerRow[0] = numOfItems;
//  }
//
//  var paddingy = (canvasHeight - rows * radius) / (rows + 1);
//  var y = paddingy;
//
//  var positions = [];
//
//  for (var i = 0; i < rows; i++) {
//    var colsForCurrent = colsPerRow[i];
//    var paddingx = (canvasWidth - colsForCurrent * radius) / (colsForCurrent + 1);
//    var x = paddingx;
//    for (var j = 0; j < colsForCurrent; j++) {
//      positions.push({
//        x: x,
//        y: y
//      });
//      x += radius + paddingx;
//    }
//
//    y += radius + paddingy;
//  }
//
//  return positions;
//};

var TemplateNewItemMenu = Vue.extend({
  template: `<div class="template-new-item-menu">
               <slot></slot>
               <canvas></canvas>
             </div>`,
  attached: function() {
    this.canvas = this.$el.getElementsByTagName('canvas')[0];

    var slots = [];
    for (var i = 0; i < this.$el.children.length; i++) {
      var child = this.$el.children[i];
      if (child.tagName.toLowerCase() !== 'canvas') {
        slots.push(child);
      }
    }
    this.onSlotsChanged(slots);

    this.canvas.addEventListener('mousemove', this.mouseMove);
    this.canvas.addEventListener('mousedown', this.mouseDown);
    this.canvas.addEventListener('mouseup', this.mouseUp);
    this.canvas.addEventListener('mouseout', this.mouseOut);
  },
  detached: function() {
    this.canvas.removeEventListener('mousemove', this.mouseMove);
    this.canvas.removeEventListener('mousedown', this.mouseDown);
    this.canvas.removeEventListener('mouseup', this.mouseUp);
    this.canvas.removeEventListener('mouseout', this.mouseOut);
  },
  methods: {
    onSlotsChanged: function(slots) {
      this.resolvedItems = null;

      var promises = slots.map((slot) => {
        return new Promise(function(resolve) {
          var img = $(slot).find('img')[0];
          if (img.complete) {
            resolve(slot);
          } else {
            img.onload = function() {
              resolve(slot);
            };
          }
        });
      });

      Promise.all(promises).then((loadedImagesSlots) => {
        // Scale 2 for retina
        var canvasWidth = this.$el.offsetWidth;
        var canvasHeight = this.$el.offsetHeight;

        $(this.canvas).css('width', canvasWidth).css('height', canvasHeight);

        this.canvas.width = canvasWidth * 2;
        this.canvas.height = canvasHeight * 2;

        this.canvas.getContext('2d').scale(2, 2);

        var numOfItems = loadedImagesSlots.length;
        var positions = positionElements(numOfItems, canvasWidth, canvasHeight, RADIUS);

        this.resolvedItems = [];
        for (var i = 0; i < numOfItems; i++) {
          var slot = loadedImagesSlots[i];
          var position = positions[i];
          this.resolvedItems.push({
            x: position.x,
            y: position.y,
            img: $(slot).find('img')[0],
            label: $(slot).find('.label').text(),
            instance: slot
          });
        }

        this.draw();
      });
    },

    mouseMove: function(e) {
      var offset = $(this.canvas).offset();
      this.mouseX = e.pageX - offset.left;
      this.mouseY = e.pageY - offset.top;

      if (this.mouseX < 0 || this.mouseX > this.canvas.width) {
        this.mouseX = undefined;
      }
      if (this.mouseY < 0 || this.mouseY > this.canvas.height) {
        this.mouseY = undefined;
      }
      this.draw();
    },

    mouseOut: function() {
      this.mouseX = undefined;
      this.mouseY = undefined;
      this.draw();
    },

    mouseDown: function() {
      this.isMouseDown = true;
      this.draw();
    },

    mouseUp: function() {
      this.isMouseDown = false;
      this.draw();

      var closest = this.getClosest();
      if (closest) {
        $(closest.instance).trigger('click');
      }
    },

    animate: function(element, position) {
      var shouldAnimate = false;
      var dx = Math.round(position.x - element.x);
      var dy = Math.round(position.y - element.y);
      if (dx !== 0) {
        element.x = Math.round(element.x + dx / Math.abs(dx));
        shouldAnimate = true;
      }

      if (dy !== 0) {
        element.y += Math.round(element.x + dy / Math.abs(dy));
        shouldAnimate = true;
      }

      if (shouldAnimate) {
        this.draw();
        this.animationRequestId = requestAnimationFrame(() => {
          this.animate(element, position);
        });
      }
    },

    draw: function() {
      if (!this.resolvedItems) {
        return;
      }

      requestAnimationFrame(this.doDraw);
    },

    doDraw: function() {
      let ctx = this.canvas.getContext('2d');
      ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
      ctx.save();

      let closestItem = this.getClosest();

      for (let i = 0; i < this.resolvedItems.length; i++) {
        let currentItem = this.resolvedItems[i];
        ctx.save();

        let scale = this.getDistance(currentItem);

        if (currentItem === closestItem && this.isMouseDown) {
          scale += 0.1;
        }

        ctx.translate(currentItem.x, currentItem.y);
        ctx.translate(RADIUS, RADIUS);
        ctx.scale(scale, scale);

        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.arc(0, 0, RADIUS, 0, 2 * Math.PI, false);
        ctx.fill();
        ctx.closePath();

        ctx.drawImage(currentItem.img, -RADIUS / 2, -RADIUS / 2, RADIUS, RADIUS);

        if (currentItem === closestItem) {
          ctx.strokeStyle = '#727173';
          ctx.lineWidth = 5;
          ctx.stroke();

          ctx.fillStyle = '#3399cc';
          ctx.font = '18px Arial';
          ctx.textAlign = 'center';
          ctx.fillText(currentItem.label, 0, RADIUS + 20);
        } else {
          ctx.strokeStyle = '#727173';
          ctx.lineWidth = 1;
          ctx.stroke();
        }

        ctx.restore();
      }

      ctx.restore();
    },

    getClosest: function() {
      if ((!this.mouseX && this.mouseX !== 0) ||
         (!this.mouseY && this.mouseY !== 0)) {
        return null;
      }

      var closest;
      var closestItem;

      for (let i = 0; i < this.resolvedItems.length; i++) {
        let item = this.resolvedItems[i];
        let dx = item.x + RADIUS - this.mouseX;
        let dy = item.y + RADIUS - this.mouseY;

        let dist = Math.sqrt(dx * dx + dy * dy);

        if (!closest) {
          closest = dist;
          closestItem = item;
        } else if (dist < closest) {
          closest = dist;
          closestItem = item;
        }

      }

      return closestItem;
    },

    getDistance: function(item) {
      if ((!this.mouseX && this.mouseX !== 0) ||
         (!this.mouseY && this.mouseY !== 0)) {
        return 0.6;
      }

      let dx = item.x + RADIUS - this.mouseX;
      let dy = item.y + RADIUS - this.mouseY;

      let dist = Math.sqrt(dx * dx + dy * dy);
      let scale = 1 - (dist / 200) * 0.7;

      scale = Math.max(scale, 0.6);

      return scale;
    }
  }
});

Vue.component('template-new-item-menu', TemplateNewItemMenu);

export default TemplateNewItemMenu;

