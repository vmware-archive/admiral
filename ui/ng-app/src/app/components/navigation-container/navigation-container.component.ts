import { Component, Input, Output, OnInit, OnDestroy, EventEmitter } from '@angular/core';
import { slideAndFade } from '../../utils/transitions';
import { ViewExpandRequestService } from '../../services/view-expand-request.service';
import { Router, ActivatedRoute, Route, RoutesRecognized, NavigationEnd, NavigationCancel } from '@angular/router';
import { Subscription } from 'rxjs';

@Component({
  selector: 'navigation-container',
  template: `
    <div *ngIf="type === 'fullScreenSlide' || type === 'default'"
        [@slideAndFade]="type === 'default' ? 'disabled' : 'active'"
        [ngClass]="{'full-screen': type === 'fullScreenSlide' }">
      <ng-content></ng-content>
    </div>
  `,
  animations: [slideAndFade()]
})
export class NavigationContainerComponent implements OnInit, OnDestroy {
  private oldComponent: any;
  private routeObserve: Subscription;
  type: string;
  @Input() typePerComponent: Map<string, NavigationContainerType>;

  constructor(private router: Router, private route: ActivatedRoute,
    private viewExpandRequestor: ViewExpandRequestService) {}

  ngOnInit() {
    this.routeObserve = this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        var newComponent: any = this.route.children.length != 0 && this.route.children[0].component;
        if (newComponent != this.oldComponent) {
          this.oldComponent = newComponent;
          var selectedType = this.typePerComponent[newComponent.name] || NavigationContainerType.None;
          this.type = selectedType.toString();
          this.viewExpandRequestor.requestFullScreen(selectedType === NavigationContainerType.Fullscreen);
        }
      }
    });
  }

  ngOnDestroy() {
    this.routeObserve.unsubscribe();
  }
}

export enum NavigationContainerType {
    Fullscreen = <any>'fullScreenSlide',
    Default = <any>'default',
    None = <any>'none'
}