import { initHarborConfig } from './../../init-harbor-config';
import { Ajax } from './../../utils/ajax.service';
import { Utils } from './../../utils/utils';
import { Links } from './../../utils/links';
import { DocumentService } from './../../utils/document.service';
import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'tag-details-containers',
  templateUrl: './tag-details-containers.component.html',
  styleUrls: ['./tag-details-containers.component.scss']
})
export class TagDetailsContainersComponent implements OnInit {

  @Input()
  tagId;

  @Input()
  repositoryId;

  containers: any[] = [];

  private static hbrInfoPromise;
  private static hbrRegistryUrl;

  constructor(private ds: DocumentService, private ajax: Ajax) { }

  ngOnInit() {
    this.getHbrRegistryUrl().then(hbrRegistryUrl => {
      if (!hbrRegistryUrl) {
        return;
      }

      let harborImage = Utils.getHbrContainerImage(hbrRegistryUrl, this.repositoryId, this.tagId);
      let queryOptions = {
        image: harborImage,
        _strictFilter: true
      }
      this.ds.list(Links.CONTAINERS, queryOptions).then(result => {
        this.containers = result.documents;
      });
    });
  }

  getHbrRegistryUrl() {
    if (!TagDetailsContainersComponent.hbrInfoPromise) {
      TagDetailsContainersComponent.hbrInfoPromise = new Promise(resolve => {
        return this.ajax.get(initHarborConfig().systemInfoEndpoint).then(info => {
          TagDetailsContainersComponent.hbrRegistryUrl = info && info.registry_url;
          return TagDetailsContainersComponent.hbrRegistryUrl;
        }).catch(e => {
          return null;
        });
      });
    }

    if (!TagDetailsContainersComponent.hbrRegistryUrl) {
      return TagDetailsContainersComponent.hbrInfoPromise;
    } else {
      return Promise.resolve(TagDetailsContainersComponent.hbrRegistryUrl);
    }
  }

  getContainerId(container) {
    return Utils.getDocumentId(container.documentSelfLink);
  }

}
