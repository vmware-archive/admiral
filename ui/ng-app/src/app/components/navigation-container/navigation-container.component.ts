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
  @Input() typePerComponent: Map<any, NavigationContainerType>;
  @ViewChild('contentHolder') contentHolder;

  constructor(private router: Router, private route: ActivatedRoute,
    private viewExpandRequestor: ViewExpandRequestService) {}

  ngOnInit() {
    this.routeObserve = this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        var newComponent: any = this.route.children.length != 0 && this.route.children[0].component;
        this.handleNewComponent(newComponent);
      }
    });
  }

  handleNewComponent(newComponent) {
    if (newComponent != this.oldComponent) {
      this.oldComponent = newComponent;
      var selectedType = this.typePerComponent[newComponent] || NavigationContainerType.None;
      this.type = selectedType.toString();
      this.viewExpandRequestor.requestFullScreen(selectedType === NavigationContainerType.Fullscreen);

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