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

import { Component, Input, QueryList, OnInit, OnChanges, SimpleChanges, ContentChild,ContentChildren, ViewChild, ViewChildren, TemplateRef, HostListener, ViewEncapsulation } from '@angular/core';
import { searchConstants } from 'admiral-ui-common';
import * as I18n from 'i18next';
import { DocumentService, DocumentListResult } from '../../utils/document.service';
import { Utils, CancelablePromise } from '../../utils/utils';
import { Router, ActivatedRoute, Route, RoutesRecognized, NavigationEnd, NavigationCancel } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';

@Component({
  selector: 'grid-view',
  templateUrl: './grid-view.component.html',
  styleUrls: ['./grid-view.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class GridViewComponent implements OnInit, OnChanges {
  loading: boolean;
  _items: any[] = [];
  @Input() serviceEndpoint: string;
  @Input() searchPlaceholder: string;
  @Input() searchSuggestionProperties: Array<string>;
  @Input() searchQueryOptions: any;
  @Input() projectLink: string;

  @ViewChildren('cardItem') cards;
  @ViewChild('itemsHolder') itemsHolder;
  @ContentChild(TemplateRef) gridItemTmpl;
  showCardView: boolean = true;
  itemTmplCard: any;
  itemTmplList: any;
  cardStyles = [];
  itemsHolderStyle: any = {};
  layoutTimeout;
  querySub: Subscription;
  routerSub: Subscription;
  totalItemsCount: number;
  loadedPages: number = 0;
  nextPageLink: string;
  loadingPromise: CancelablePromise<DocumentListResult>;
  hidePartialRows: boolean = false;
  loadPagesTimeout;

  searchOccurrenceProperties = [{
    name: searchConstants.SEARCH_OCCURRENCE.ALL,
    label: I18n.t('occurrence.all', {ns: 'base'})
  }, {
    name: searchConstants.SEARCH_OCCURRENCE.ANY,
    label: I18n.t('occurrence.any', {ns: 'base'})
  }];

  constructor(protected service: DocumentService, private router: Router, private route: ActivatedRoute) { }

  ngOnInit() {
    const urlTree = this.router.createUrlTree(['.'], { relativeTo: this.route });
    const currentPath = this.router.serializeUrl(urlTree);

    this.routerSub = this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd && event.url === currentPath) {
        this.refresh();
      }
    });

    this.querySub = this.route.queryParams.subscribe(queryParams => {
      this.searchQueryOptions = queryParams;
      this.refresh(true);
    });
  }

  ngAfterViewInit() {
    this.cards.changes.subscribe(() => {
      this.throttleLayout();
    });
    this.throttleLayout();
  }

  ngOnDestroy() {
    if (this.querySub) {
      this.querySub.unsubscribe();
    }
    if (this.routerSub) {
      this.routerSub.unsubscribe();
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes.serviceEndpoint && this.serviceEndpoint || changes.projectLink) {
      this.refresh();
    }
  }

  @Input()
  set items(value: any[]) {
    let newCardStyles = value.map((d, index) => {
      if (index < this.cardStyles.length) {
        return this.cardStyles[index];
      }
      return {
        opacity: '0',
        overflow: 'hidden'
        };
    });
    this.cardStyles = newCardStyles;
    this._items = value;
  }

  get items() {
    return this._items;
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
    let items = el.querySelectorAll('.card-item');
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

    let visible = length;
    if (this.hidePartialRows && this.totalItemsCount && length !== this.totalItemsCount) {
      visible = rows * columnsToUse;
    }

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

  onCardEnter(i) {
    this.cardStyles[i].overflow = 'visible';
  }

  onCardLeave(i) {
    this.cardStyles[i].overflow = 'hidden';
  }

  onScroll() {
    console.log('onScroll');
    if (this.nextPageLink) {
      this.loadNextPage();
    }
  }

  refresh(resetLoadedPages?) {
    var pagesToLoad = resetLoadedPages ? 1 : this.loadedPages;

    clearTimeout(this.loadPagesTimeout);
    this.loadPagesTimeout = setTimeout(() => this.doLoadPages(pagesToLoad), 0);
  }

  trackByFn(index, item){
    return item.documentSelfLink;
  }

  private doLoadPages(pagesToLoad) {
    console.log('doLoadPages');
    let loadMore = () => {
      if (pagesToLoad > this.loadedPages && this.nextPageLink) {
        this.loadNextPage().then(loadMore);
      }
    };

    this.list().then(loadMore);
  }

  private list() {
    console.log('list');
    if (this.loadingPromise) {
      this.loadingPromise.cancel();
    }

    // Partial rows are displayed only when data is provided from outside,
    // Otherwise for better UX when doing infinite scroll show only full rows
    this.hidePartialRows = true;

    this.loading = true;
    this.loadedPages = 0;
    this.loadingPromise = new CancelablePromise(this.service.list(this.serviceEndpoint, this.searchQueryOptions, this.projectLink));
    return this.loadingPromise.getPromise()
    .then(result => {
      this.loading = false;
      this.totalItemsCount = result.totalCount;
      this.items = result.documents;
      this.nextPageLink = result.nextPageLink;
      this.loadedPages++;
    }).catch(e => {
      if (e) {
        if (e.isCanceled) {
          // ok to be canceled
        } else {
          console.error('Failed loading items ', e)
        }
      }
    })
  }

  private loadNextPage() {
    if (this.loadingPromise) {
      this.loadingPromise.cancel();
    }

    this.loading = true;
    this.loadingPromise = new CancelablePromise(this.service.loadNextPage(this.nextPageLink, this.projectLink));
    return this.loadingPromise.getPromise()
    .then(result => {
      this.loading = false;
      this.items = this.items.concat(result.documents);
      this.nextPageLink = result.nextPageLink;
      this.loadedPages++;
    }).catch(e => {
      if (e){
        console.log(e);
        if (e.isCanceled) {
          // ok to be canceled
        } else {
          console.error('Failed loading items ', e)
        }
      }
    });
  }
}
