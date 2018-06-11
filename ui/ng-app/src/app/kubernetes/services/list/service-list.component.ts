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

import { Component, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from "@angular/router";
import { Links } from '../../../utils/links';
import { AutoRefreshComponent } from "../../../components/base/auto-refresh.component";
import { GridViewComponent } from "../../../components/grid-view/grid-view.component";
import { DocumentService } from "../../../utils/document.service";
import { ProjectService } from "../../../utils/project.service";
import { FT } from "../../../utils/ft";
import { Utils } from "../../../utils/utils";

@Component({
  selector: 'service-list',
  templateUrl: './service-list.component.html',
  styleUrls: ['./service-list.component.scss']
})
/**
 * Kubernetes services list view.
 */
export class ServiceListComponent extends AutoRefreshComponent {
    @ViewChild('gridView') gridView: GridViewComponent;
    serviceEndpoint = Links.SERVICES;
    projectLink: string;


    selectedItem: any;

    constructor(protected service: DocumentService, protected projectService: ProjectService,
                protected router: Router, protected route: ActivatedRoute) {
        super(router, route, FT.allowHostEventsSubscription(),
            Utils.getClustersViewRefreshInterval(), true);

        projectService.activeProject.subscribe((value) => {
            if (value && value.documentSelfLink) {
                this.projectLink = value.documentSelfLink;
            } else if (value && value.id) {
                this.projectLink = value.id;
            } else {
                this.projectLink = undefined;
            }
        });
    }

    ngOnInit(): void {
        this.refreshFnCallScope = this.gridView;
        this.refreshFn = this.gridView.autoRefresh;

        super.ngOnInit();
    }

    isItemSelected(item: any) {
        return item === this.selectedItem;
    }

    toggleItemSelection($event, item) {
        $event.stopPropagation();

        if (this.isItemSelected(item)) {
            // clear selection
            this.selectedItem = null;
        } else {
            this.selectedItem = item;
        }
    }
}
