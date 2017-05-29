import { Component, OnInit, OnDestroy, ViewChild, ViewEncapsulation } from '@angular/core';
import { Router, ActivatedRoute, Route, RoutesRecognized, NavigationEnd, NavigationCancel } from '@angular/router';
import { Subscription } from 'rxjs';
import { ViewExpandRequestService } from '../../services/view-expand-request.service';

@Component({
  selector: 'app-former-view',
  templateUrl: './former-view.component.html',
  styleUrls: ['./former-view.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class FormerViewComponent implements OnInit, OnDestroy {
  private routeObserve: Subscription;

  @ViewChild('frameHolder') frameHolder;

  private static iframe: HTMLIFrameElement;

  constructor(private router: Router, private route: ActivatedRoute, private viewExpandRequestService: ViewExpandRequestService) {}

  ngOnInit() {
    if (!FormerViewComponent.iframe) {
      FormerViewComponent.iframe = document.createElement('iframe');
    }

    this.routeObserve = this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        console.log('event.url ' + event.url);

        let url = event.url.substring('/home/'.length);
        let route = this.route;
        console.log('route ', route, 'url', url);

        if (!this.frameHolder.nativeElement.querySelector('iframe')) {
          this.frameHolder.nativeElement.appendChild(FormerViewComponent.iframe);
        }

        FormerViewComponent.iframe.src = '../index-no-navigation.html#' + url;
        FormerViewComponent.iframe.id = 'former-view-frame';

        this.viewExpandRequestService.requestExpandScreen(true);
      }
    });
  }

  ngOnDestroy() {
    this.routeObserve.unsubscribe();
    this.viewExpandRequestService.requestExpandScreen(false);
  }

}
