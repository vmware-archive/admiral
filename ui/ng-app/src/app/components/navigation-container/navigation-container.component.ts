import { Component, Input, Output, OnInit, OnDestroy, EventEmitter, ViewChild } from '@angular/core';
import { slideAndFade } from '../../utils/transitions';
import { ViewExpandRequestService } from '../../services/view-expand-request.service';
import { Router, ActivatedRoute, Route, RoutesRecognized, NavigationStart, NavigationEnd, NavigationCancel } from '@angular/router';
import { Subscription } from 'rxjs';

@Component({
  selector: 'navigation-container',
  template: `
    <div [ngStyle]="contentStyle" [ngClass]="{'full-screen': type === 'fullScreenSlide' }">
        <ng-content></ng-content>
    </div>
  `,
  animations: [slideAndFade()]
})
export class NavigationContainerComponent implements OnInit, OnDestroy {
  private oldComponent: string;
  private routeObserve: Subscription;

  contentStyle: any = {
    opacity: '0',
    pointerEvents: 'none',
    transition: 'none'
  };

  type: string;
  @ViewChild('contentHolder') contentHolder;

  constructor(private router: Router, private route: ActivatedRoute,
    private viewExpandRequestor: ViewExpandRequestService) {}

  ngOnInit() {
    this.routeObserve = this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        var child: Route = (this.route.children.length != 0 && this.route.children[0]) || {};
        this.handleNewComponent(child);
      }
    });
  }

  hasFullscreenParent(route) {
    var routeData = route.data && route.data.value;
    if (routeData && routeData.navigationContainerType === NavigationContainerType.Fullscreen) {
      return true;
    }

    if (!route.parent) {
      return false;
    }

    return this.hasFullscreenParent(route.parent);
  }

  handleNewComponent(newRoute) {
    var newComponent: any = newRoute.component;
    var navigationContainerType = newRoute.data && newRoute.data.value && newRoute.data.value.navigationContainerType;

    let selectedType;
    if (newComponent != this.oldComponent) {
      this.oldComponent = newComponent;
      selectedType = navigationContainerType || NavigationContainerType.None;
      this.type = selectedType.toString();

      if (selectedType === NavigationContainerType.None) {
        this.contentStyle.opacity = '0';
        this.contentStyle.pointerEvents = 'none';
      } else {
        if (selectedType === NavigationContainerType.Fullscreen) {
          this.contentStyle.transition = 'all 0.3s ease-in';
        } else {
          this.contentStyle.transition = 'none';
        }
        this.contentStyle.opacity = '1';
        this.contentStyle.pointerEvents = 'all';
      }
    }

    let isFullscreen = this.hasFullscreenParent(newRoute);
    this.viewExpandRequestor.requestFullScreen(isFullscreen);
  }

  ngOnDestroy() {
    this.routeObserve.unsubscribe();

    let isFullscreen = this.hasFullscreenParent(this.route);
    this.viewExpandRequestor.requestFullScreen(isFullscreen);
  }
}

export enum NavigationContainerType {
    Fullscreen = <any>'fullScreenSlide',
    Default = <any>'default',
    None = <any>'none'
}