import { Component, OnInit, OnDestroy, ViewChild, ViewEncapsulation, Input } from '@angular/core';
import { Router, ActivatedRoute, Route, RoutesRecognized, NavigationEnd, NavigationCancel } from '@angular/router';
import { Subscription } from 'rxjs';
import { ViewExpandRequestService } from '../../services/view-expand-request.service';
import { DomSanitizer } from '@angular/platform-browser';

@Component({
  selector: 'former-view',
  templateUrl: './former-view.component.html',
  styleUrls: ['./former-view.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class FormerViewComponent {
  @Input()
  path: String;

  constructor(private sanitizer: DomSanitizer) {}

  get src() {
    let path = this.path || 'containers';
    return this.sanitizer.bypassSecurityTrustResourceUrl('../index-no-navigation.html#' + path);
  }
}

@Component({
  selector: 'former-view-placeholder',
  template: '<div></div>'
})
export class FormerPlaceholderViewComponent {
}
