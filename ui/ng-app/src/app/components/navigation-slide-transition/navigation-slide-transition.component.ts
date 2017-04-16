import { Component, Input, Output, OnInit, EventEmitter } from '@angular/core';
import { slideAndFade } from '../../utils/transitions';
import { Router, Route, RoutesRecognized, NavigationEnd, NavigationCancel } from '@angular/router';

@Component({
  selector: 'navigation-slide-transition',
  template: `<div [@slideAndFade]="isRouteActivated && 'active'" class="details-view"><ng-content></ng-content></div>`,
  animations: [slideAndFade()]
})
export class NavigationSlideTransitionComponent implements OnInit {
  private isRouteActivated: boolean = false;
  @Input() parentComponentName: string;
  @Output() routeActivationChange: EventEmitter<boolean> = new EventEmitter<boolean>();
  private pathToParentComponent: string;

  constructor(private router: Router) {}

  ngOnInit() {
    this.pathToParentComponent = this.buildPathToRoute(this.router.config, this.parentComponentName);
    if (!this.pathToParentComponent) {
      throw new Error('Could not find component with name ' + this.parentComponentName);
    }

    this.router.events.subscribe((event) => {
      let hasMatch = false;
      if (event instanceof NavigationEnd) {
        let url = event.url;
        if (url.indexOf(this.pathToParentComponent) === 0) {
          url = url.substring(0, this.pathToParentComponent.length);
          hasMatch = url.length > 0;
        }

        if (hasMatch) {
          if (!this.isRouteActivated) {
            this.routeActivationChange.emit(true);
          }
          this.isRouteActivated = true;
        } else {
          if (this.isRouteActivated) {
            this.routeActivationChange.emit(false);
          }
          this.isRouteActivated = false;
        }
      }
    });
  }

  buildPathToRoute(routes: Route[], componentName: string): string {
    if (!routes) {
      return null;
    }
    for (var i = 0; i < routes.length; i++) {
      let route = routes[i];
      if (route.component && route.component.name === componentName) {
        return '/' + route.path + '/';
      }
      let path = this.buildPathToRoute(route.children, componentName);
      if (path) {
        return '/' + route.path + path;
      }
    };
    return null;
  }
}