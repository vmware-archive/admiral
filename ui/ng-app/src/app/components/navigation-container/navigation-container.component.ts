import { Component, Input, Output, OnInit, EventEmitter } from '@angular/core';
import { slideAndFade } from '../../utils/transitions';
import { Router, ActivatedRoute, Route, RoutesRecognized, NavigationEnd, NavigationCancel } from '@angular/router';

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
export class NavigationContainerComponent implements OnInit {
  private oldComponent: any;
  @Input() type: string;
  @Output() routeActivationChange: EventEmitter<any> = new EventEmitter<any>();

  constructor(private router: Router, private route: ActivatedRoute) {}

  ngOnInit() {
    this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        var newComponent = this.route.children.length != 0 && this.route.children[0].component;
        if (newComponent != this.oldComponent) {
          this.oldComponent = newComponent;
          this.routeActivationChange.emit(newComponent);
        }
      }
    });
  }
}