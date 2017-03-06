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

import { Component, Input, QueryList, OnInit, ContentChild, ContentChildren, ViewChild, ViewChildren, TemplateRef, HostListener, ViewEncapsulation } from '@angular/core';

@Component({
  selector: 'grid-view',
  templateUrl: './grid-view.component.html',
  styleUrls: ['./grid-view.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class GridViewComponent implements OnInit {
  @Input() items: any[];
  @Input() count: number;
  @Input() loading: boolean;

  @ViewChildren('cardItem') cards;
  @ViewChild('itemsHolder') itemsHolder;
  @ContentChild(TemplateRef) gridItemTmpl;
  showCardView: boolean = true;
  itemTmplCard: any;
  itemTmplList: any;
  cardStyles = [];
  itemsHolderStyle: any = {};
  layoutTimeout;

  constructor() { }

  ngOnInit() {
  }

  ngAfterContentInit() {
  }

  ngAfterViewInit() {
    this.cards.changes.subscribe(() => {
      this.throttleLayout();
    });
    this.throttleLayout();
  }

  @HostListener('window:resize')
  onResize(event) {
    this.throttleLayout();
  }

  toggleCardView(showCardView) {
    this.showCardView = showCardView;
    this.throttleLayout();
  }

  throttleLayout() {
    clearTimeout(this.layoutTimeout);
    this.layoutTimeout = setTimeout(() => {
      this.layout.call(this);
    }, 40);
  }

  layout() {
    let el = this.itemsHolder.nativeElement;

    let width = el.offsetWidth;
    let items = el.children;
    let length = items.length;
    if (length === 0) {
      el.height = 0;
      return;
    }

    let itemsHeight = [];
    for (let i = 0; i < length; i++) {
      itemsHeight[i] = items[i].offsetHeight;
    }

    let height = Math.max.apply(null, itemsHeight);
    var itemsStyle = window.getComputedStyle(items[0]);

    var minWidthStyle = itemsStyle['min-width'];
    var maxWidthStyle = itemsStyle['max-width'];

    let minWidth = parseInt(minWidthStyle, 10);
    let maxWidth = parseInt(maxWidthStyle, 10);

    let marginHeight =
        parseInt(itemsStyle['margin-top'], 10) + parseInt(itemsStyle['margin-bottom'], 10);
    let marginWidth =
        parseInt(itemsStyle['margin-left'], 10) + parseInt(itemsStyle['margin-right'], 10);

    let columns = Math.floor(width / (minWidth + marginWidth));
    if (minWidthStyle === '100%') {
      columns = 1;
    }

    let columnsToUse = Math.max(Math.min(columns, length), 1);
    let rows = Math.floor(length / columnsToUse);
    let itemWidth = Math.min(Math.floor(width / columnsToUse) - marginWidth, maxWidth);
    let itemSpacing = columnsToUse === 1 || columns > length ? marginWidth :
        (width - marginWidth - columnsToUse * itemWidth) / (columnsToUse - 1);

    let visible = !this.count || length === this.count ? length : rows * columnsToUse;

    let count = 0;
    for (let i = 0; i < visible; i++) {
      let item = items[i];
      var itemStyle = window.getComputedStyle(item);

      var left = (i % columnsToUse) * (itemWidth + itemSpacing);
      var top = Math.floor(count / columnsToUse) * (height + marginHeight);

      // trick to show nice apear animation, where the item is already positioned,
      // but it will pop out
      // var oldTransform = itemStyle['transform'];
      // if (!oldTransform || oldTransform === 'none') {
      //   item.classList.add('notransition');
      //   item.style.transform = 'translate(' + left + 'px,' + top + 'px) scale(0)';
      //   this.reflow(item);
      // }

      this.cardStyles[i] = {
        transform:  'translate(' + left + 'px,' + top + 'px) scale(1)',
        width: itemWidth + 'px',
        transition: null
      };

      if (!item.classList.contains('context-selected')) {
        var itemHeight = itemsHeight[i];
        if (itemStyle.display == 'none' && itemHeight !== 0) {
          this.cardStyles[i].display = null;
        }
        if (itemHeight !== 0) {
          count++;
        }
      }
    }

    for (let i = visible; i < length; i++) {
      this.cardStyles[i] = {
        display: 'none'
      };
    }

    this.itemsHolderStyle= {
      height: Math.ceil(count / columnsToUse) * (height + marginHeight)
    };
  }

  getStyle(index): any {
    if (this.cardStyles[index]) {
      return this.cardStyles[index];
    } else {
      return {};
    }
  }

  reflow(el) {
    el.offsetHeight;
  }
}
