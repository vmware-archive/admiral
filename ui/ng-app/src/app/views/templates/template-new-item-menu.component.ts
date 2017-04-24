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

import { Component, OnInit, ViewChild, ContentChildren, AfterContentInit, ViewEncapsulation } from '@angular/core';

const RADIUS = 40;
const ANIMATION_DURATION = 400;
const PLUS_BUTTON_WIDTH = 18;

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
      y: y,
      initialX: canvasWidth / 2 - radius,
      initialY: canvasHeight / 2 - radius
    });
    angle += step;
  }

  return positions;
};

var easeOutQuart = function(t) {
  return 1 - (--t) * t * t * t;
};

var drawRoundLine = function(ctx, canvasWidth, canvasHeight, lineWidth, lineHeight) {
  var radius = Math.min(lineWidth, lineHeight) / 3;

  var x = canvasWidth / 2 - lineWidth / 2;
  var y = canvasHeight / 2 - lineHeight / 2;

  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.lineTo(x + lineWidth - radius, y);
  ctx.quadraticCurveTo(x + lineWidth, y, x + lineWidth, y + radius);
  ctx.lineTo(x + lineWidth, y + lineHeight - radius);
  ctx.quadraticCurveTo(x + lineWidth, y + lineHeight, x + lineWidth - radius, y + lineHeight);
  ctx.lineTo(x + radius, y + lineHeight);
  ctx.quadraticCurveTo(x, y + lineHeight, x, y + lineHeight - radius);
  ctx.lineTo(x, y + radius);
  ctx.quadraticCurveTo(x, y, x + radius, y);
  ctx.closePath();
  ctx.fill();
};

@Component({
  selector: 'template-new-item-menu',
  template: `<div class="template-new-item-menu" #holderEl>
               <ng-content></ng-content>
               <canvas (mousemove)="mouseMove($event)"
                       (mousedown)="mouseDown()"
                       (mouseup)="mouseUp()"
                       (mouseover)="mouseOver()"
                       (mouseout)="mouseOut()" #canvasEl></canvas>
             </div>`,
  styleUrls: ['./template-new-item-menu.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class TemplateNewItemMenu implements OnInit, AfterContentInit {
  @ViewChild('holderEl') holderEl;
  @ViewChild('canvasEl') canvasEl;

  private resolvedItems;
  private canvasWidth;
  private canvasHeight;
  private mouseX;
  private mouseY;
  private isMouseDown;
  private animationRequestId;
  private animationTime;

  ngOnInit() {
  }

  ngAfterContentInit() {
    let el:HTMLElement = this.holderEl.nativeElement
    var slots = [];
    for (var i = 0; i < el.children.length; i++) {
      var child = el.children[i];
      if (child.tagName.toLowerCase() !== 'canvas') {
        slots.push(child);
      }
    }
    this.onSlotsChanged(slots);
  }

  onSlotsChanged(slots) {
    this.resolvedItems = null;

    var promises = slots.map((slot) => {
      return new Promise(function(resolve) {
        var img = slot.querySelector('img');
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
      let el:HTMLElement = this.holderEl.nativeElement
      let canvas:HTMLCanvasElement = this.canvasEl.nativeElement
      // Scale 2 for retina
      this.canvasWidth = el.offsetWidth;
      this.canvasHeight = el.offsetHeight;

      canvas.style.width = this.canvasWidth;
      canvas.style.height = this.canvasHeight;

      canvas.width = this.canvasWidth * 2;
      canvas.height = this.canvasHeight * 2;

      canvas.getContext('2d').scale(2, 2);

      var numOfItems = loadedImagesSlots.length;
      var positions = positionElements(numOfItems, this.canvasWidth, this.canvasHeight, RADIUS);

      this.resolvedItems = [];
      for (var i = 0; i < numOfItems; i++) {
        var slot: any = loadedImagesSlots[i];
        var position = positions[i];
        this.resolvedItems.push({
          x: position.x,
          y: position.y,
          initialX: position.initialX,
          initialY: position.initialY,
          img: slot.querySelector('img'),
          label: slot.querySelector('.label').innerText,
          instance: slot
        });
      }

      this.draw(true);
    });
  }

  mouseMove(e) {
    this.mouseX = e.pageX - this.canvasEl.nativeElement.offsetLeft;
    this.mouseY = e.pageY - this.canvasEl.nativeElement.offsetTop;

    if (this.mouseX < 0 || this.mouseX > this.canvasEl.nativeElement.width) {
      this.mouseX = undefined;
    }
    if (this.mouseY < 0 || this.mouseY > this.canvasEl.nativeElement.height) {
      this.mouseY = undefined;
    }

    if (!this.animationRequestId) {
      this.draw(false);
    }
  }

  mouseOver() {
    this.animate(true);
  }

  mouseOut() {
    this.mouseX = undefined;
    this.mouseY = undefined;

    this.animate(false);
  }

  mouseDown() {
    this.isMouseDown = true;
    this.draw(false);
  }

  mouseUp() {
    this.isMouseDown = false;
    this.draw(false);
    var closest = this.getClosest();
    if (closest) {
      closest.instance.click();
    }
  }

  draw(initial) {
    requestAnimationFrame(() => {
      if (!this.animationRequestId) {
        this.doAnimate(!initial, null);
      }
    });
  }

  animate(forward) {
    requestAnimationFrame(() => {
      cancelAnimationFrame(this.animationRequestId);

      var startTime = (new Date()).getTime();
      if (this.animationTime) {
        startTime -= (ANIMATION_DURATION - this.animationTime);
      }
      this.doAnimate(forward, startTime);
    });
  }

  doAnimate(forward, startTime) {
    if (!this.resolvedItems) {
      return;
    }

    let closestItem = this.getClosest();

    var t;
    var time;

    if (!startTime) {
      t = 1;
    } else {
      time = (new Date()).getTime() - startTime;
      t = Math.min(time / ANIMATION_DURATION, 1);
    }
    if (!forward) {
      t = 1 - t;
    }
    var ease = easeOutQuart(t);

    let ctx = this.canvasEl.nativeElement.getContext('2d');
    ctx.clearRect(0, 0, this.canvasEl.nativeElement.width, this.canvasEl.nativeElement.height);
    ctx.save();

    for (let i = 0; i < this.resolvedItems.length; i++) {
      let currentItem = this.resolvedItems[i];
      ctx.save();

      let scale = this.getDistance(currentItem);

      var currentItemDisabled = currentItem.instance.getAttribute('data-disabled');

      if (currentItem === closestItem && this.isMouseDown && !currentItemDisabled) {
        scale += 0.1;
      }

      var x = currentItem.initialX + (currentItem.x - currentItem.initialX) * ease;
      var y = currentItem.initialY + (currentItem.y - currentItem.initialY) * ease;

      ctx.globalAlpha = currentItemDisabled ? Math.min(0.5, ease) : ease;

      ctx.translate(x, y);
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
        ctx.lineWidth = currentItemDisabled ? 1 : 5;

        ctx.stroke();

        ctx.fillStyle = currentItemDisabled ? '#727173' : '#3399cc';
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

    ctx.save();
    ctx.globalAlpha = Math.max(0, 1 - ease);
    ctx.fillStyle = '#bababa';
    drawRoundLine(ctx, this.canvasWidth, this.canvasHeight,
      PLUS_BUTTON_WIDTH * 4, PLUS_BUTTON_WIDTH);
    drawRoundLine(ctx, this.canvasWidth, this.canvasHeight,
      PLUS_BUTTON_WIDTH, PLUS_BUTTON_WIDTH * 4);
    ctx.restore();

    ctx.restore();

    if (time >= 0 && time < ANIMATION_DURATION) {
      this.animationTime = time;
      this.animationRequestId = requestAnimationFrame(() => {
        this.doAnimate(forward, startTime);
      });
    } else {
      this.animationTime = 0;
      this.animationRequestId = null;
    }
  }

  getClosest() {
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
  }

  getDistance(item) {
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
