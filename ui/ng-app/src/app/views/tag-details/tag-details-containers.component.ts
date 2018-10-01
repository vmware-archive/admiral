/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { Component, OnInit, Input } from '@angular/core';
import { initHarborConfig } from '../../init-harbor-config';
import { AjaxService } from '../../utils/ajax.service';
import { DocumentService } from '../../utils/document.service';
import { Links } from '../../utils/links';
import { Utils } from '../../utils/utils';

@Component({
    selector: 'tag-details-containers',
    templateUrl: './tag-details-containers.component.html',
    styleUrls: ['./tag-details-containers.component.scss']
})
export class TagDetailsContainersComponent implements OnInit {
    @Input() tagId;
    @Input() repositoryId;

    containers: any[] = [];

    private static hbrInfoPromise;
    private static hbrRegistryUrl;

    constructor(private documentService: DocumentService, private ajax: AjaxService) {
        //
    }

    ngOnInit() {
        this.getHbrRegistryUrl().then(hbrRegistryUrl => {
            if (!hbrRegistryUrl) {
                return;
            }

            let harborImage = Utils.getHbrContainerImage(hbrRegistryUrl, this.repositoryId, this.tagId);
            let queryOptions = {
                image: harborImage,
                _strictFilter: true
            };

            this.documentService.list(Links.CONTAINERS, queryOptions).then(result => {
                this.containers = result.documents;
            }).catch(error => {
                console.error('Failed to retrieve Harbor container image', error);
            });
        }).catch(error => {
            console.error('Failed to retrieve Harbor registry url', error);
        });
    }

    getHbrRegistryUrl() {
        if (!TagDetailsContainersComponent.hbrInfoPromise) {

            TagDetailsContainersComponent.hbrInfoPromise = new Promise(resolve => {

                return this.ajax.get(initHarborConfig().systemInfoEndpoint).then(info => {
                    TagDetailsContainersComponent.hbrRegistryUrl = info && info.registry_url;

                    return TagDetailsContainersComponent.hbrRegistryUrl;
                }).catch(error => {
                    console.error('Failed to retrieve Harbor system info', error);

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
