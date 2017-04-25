import { Component, Input, Output, OnInit, OnDestroy, EventEmitter } from '@angular/core';
import { slideAndFade } from '../../utils/transitions';
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
  @Input() type: string;
  @Output() routeActivationChange: EventEmitter<any> = new EventEmitter<any>();

  constructor(private router: Router, private route: ActivatedRoute) {}

  ngOnInit() {
    this.routeObserve = this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        var newComponent = this.route.children.length != 0 && this.route.children[0].component;
        if (newComponent != this.oldComponent) {
          this.oldComponent = newComponent;
          this.routeActivationChange.emit(newComponent);
        }
      }
    });
  }

  ngOnDestroy() {
    this.routeObserve.unsubscribe();
  }
}