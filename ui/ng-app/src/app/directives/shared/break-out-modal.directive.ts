import { Directive, OnChanges, Input } from '@angular/core';

@Directive({
  selector: '[breakOutModal]'
})
export class BreakOutModalDirective implements OnChanges {

  @Input()
  public clrModalOpen: boolean;

  private expanded: boolean;
  private shrinkTimeout;

  constructor() { }

  ngOnChanges(changes) {
    clearTimeout(this.shrinkTimeout);
    if (this.clrModalOpen) {
      console.log('expand');
      this.expandIframe();
    } else {
      console.log('shrink');
      // give it time to close
      this.shrinkTimeout = setTimeout(() => {
        this.shrinkIframe();
      }, 200);
    }
  }

  private expandIframe() {
    if (this.isEmbedded() && !this.expanded) {
      var bodyRect = window.parent.document.body.getBoundingClientRect();
      var iframeRect = window.frameElement.getBoundingClientRect();

      var top = iframeRect.top;
      var left = iframeRect.left;
      var right = bodyRect.right - iframeRect.right;
      var bottom = bodyRect.bottom - iframeRect.bottom;

      window.document.body.style['position'] = 'absolute';
      window.document.body.style['top'] = top + 'px';
      window.document.body.style['left'] = left + 'px';
      window.document.body.style['right'] = right + 'px';
      window.document.body.style['bottom'] = bottom + 'px';

      var ifr = window.frameElement;
      ifr['style']['position'] = 'fixed';
      ifr['style']['top'] = '0';
      ifr['style']['left'] = '0';
      ifr['style']['right'] = '0';
      ifr['style']['bottom'] = '0';

      this.expanded = true;
    }
  }

  private shrinkIframe() {

    if (this.isEmbedded() && this.expanded) {
      window.document.body.style['position'] = 'initial';
      window.document.body.style['top'] = null;
      window.document.body.style['left'] = null;
      window.document.body.style['right'] = null;
      window.document.body.style['bottom'] = null;

      var ifr = window.frameElement;
      ifr['style']['position'] = 'initial';
      ifr['style']['top'] = null;
      ifr['style']['left'] = null;
      ifr['style']['right'] = null;
      ifr['style']['bottom'] = null;

      this.expanded = false;
    }
  }

  private isEmbedded() {
    return window.parent && window.parent != window;
  }

}

