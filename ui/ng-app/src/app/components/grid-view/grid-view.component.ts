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

import { Component, Input, QueryList, OnInit, ContentChild,ContentChildren, ViewChild, ViewChildren, TemplateRef, HostListener, ViewEncapsulation } from '@angular/core';
import { searchConstants } from 'admiral-ui-common';
import * as I18n from 'i18next';
import { DocumentService } from '../../utils/document.service';
import { Utils } from '../../utils/utils';
import { Router, ActivatedRoute, Route, RoutesRecognized, NavigationEnd, NavigationCancel } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';

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
  @Input() serviceEndpoint: string;
  @Input() searchPlaceholder: string;
  @Input() searchSuggestionProperties: Array<string>;
  @Input() searchQueryOptions: any;

  @ViewChildren('cardItem') cards;
  @ViewChild('itemsHolder') itemsHolder;
  @ContentChild(TemplateRef) gridItemTmpl;
  showCardView: boolean = true;
  itemTmplCard: any;
  itemTmplList: any;
  cardStyles = [];
  itemsHolderStyle: any = {};
  layoutTimeout;
  cardOverTimeout;
  querySub: Subscription;

  searchOccurrenceProperties = [{
    name: searchConstants.SEARCH_OCCURRENCE.ALL,
    label: I18n.t('occurrence.all')
  }, {
    name: searchConstants.SEARCH_OCCURRENCE.ANY,
    label: I18n.t('occurrence.any')
  }];

  constructor(protected service: DocumentService, private router: Router, private route: ActivatedRoute) { }

  ngOnInit() {
    this.items = [];
    this.loading = true;
    this.service.list(this.serviceEndpoint).then(result => {
      this.items = result;
      this.cardStyles = this.items.map(i => {
        return {
          opacity: '0',
          overflow: 'hidden'
         };
      });

      this.loading = false;
    });

    this.querySub = this.route.queryParams.subscribe(queryParams => {
      this.searchQueryOptions = queryParams;
    });

    this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
      }
    });
  }

  ngAfterViewInit() {
    this.cards.changes.subscribe(() => {
      this.throttleLayout();
    });
    this.throttleLayout();
  }

  ngOnDestroy() {
    this.querySub.unsubscribe();
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
    if (!this.showCardView) {
      columns = 1;
      maxWidth = width;
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
      var oldTransform = itemStyle['transform'];
      if (!oldTransform || oldTransform === 'none') {
        this.cardStyles[i] = {
          transform:  'translate(' + left + 'px,' + top + 'px) scale(0)',
          width: itemWidth + 'px',
          transition: 'none',
          overflow: 'hidden'
        };
        this.throttleLayout();
      } else {
        this.cardStyles[i] = {
          transform:  'translate(' + left + 'px,' + top + 'px) scale(1)',
          width: itemWidth + 'px',
          transition: null,
          overflow: 'hidden'
        };
      }

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

  reflow(el) {
    el.offsetHeight;
  }

  onSearch(queryOptions) {
    this.router.navigate(['.'], {
      relativeTo: this.route,
      queryParams: queryOptions
    });
  }

  cardEnter(i) {
    clearTimeout(this.cardOverTimeout);
    this.cardStyles[i].overflow = 'hidden';
    this.cardOverTimeout = setTimeout(() => {
      this.cardStyles[i].overflow = 'visible';
    }, 300);
  }

  cardLeave(i) {
    clearTimeout(this.cardOverTimeout);
    this.cardStyles[i].overflow = 'hidden';
  }
}
