import { Utils } from './../../utils/utils';
import { Component, OnInit, OnDestroy, ViewChild, ViewEncapsulation, Input } from '@angular/core';
import { Router, ActivatedRoute, Route, RoutesRecognized, NavigationEnd, NavigationCancel } from '@angular/router';
import { Subscription } from 'rxjs';
import { ViewExpandRequestService } from '../../services/view-expand-request.service';

@Component({
  selector: 'former-view',
  templateUrl: './former-view.component.html',
  styleUrls: ['./former-view.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class FormerViewComponent {

  private url: string;
  private frameLoading = false;

  @ViewChild('theFrame') theFrame;

  @Input()
  forCompute: boolean;

  @Input()
  set path(val: string) {
    val = val || 'containers';

    this.url = window.location.pathname + 'ogui/index-no-navigation.html';

    if (this.forCompute) {
      this.url += '?compute';
    }

    this.url += '#' + val;

    if (Utils.isLogin()) {
      return;
    }

    let iframeEl = this.theFrame.nativeElement;
    if (!iframeEl.src) {
      this.frameLoading = true;
      iframeEl.onload = () => {
        this.frameLoading = false;
        iframeEl.src = this.url;
      }

      iframeEl.src = this.url;
    } else if (!this.frameLoading){
      iframeEl.src = this.url;
    }
  }
}

@Component({
  selector: 'former-view-placeholder',
  template: '<div></div>'
})
export class FormerPlaceholderViewComponent {
}
