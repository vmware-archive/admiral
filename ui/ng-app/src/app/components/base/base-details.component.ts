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

import { OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from "rxjs";
import { TabbedViewComponent } from "./tabbed-view-component";
import { DocumentService } from '../../utils/document.service';
import { ErrorService } from "../../utils/error.service";
import { ProjectService } from "../../utils/project.service";
import { Utils } from "../../utils/utils";

/**
 * Base view for a single entity details.
 */
export class BaseDetailsComponent extends TabbedViewComponent implements OnInit, OnDestroy {
    id;
    entity;
    private projectLink: string;

    private routeParamsSubscription: Subscription = null;
    private routeParentParamsSubscription: Subscription = null;

    /*
     * @param {ProjectService} projectService - if this service is specified, the view will be
     * sensitive to changes of the currently selected project/business group
     */
    constructor(protected link: string, protected route: ActivatedRoute,
                protected router: Router, protected service: DocumentService,
                protected projectService: ProjectService,
                protected errorService: ErrorService) {

        super(route, router);

        if (projectService) {
            Utils.subscribeForProjectChange(projectService, (changedProjectLink) => {
                if (this.projectLink && this.projectLink !== changedProjectLink) {
                    // project selection changed
                    this.projectLink = changedProjectLink;
                    this.onProjectChange();
                } else if (!this.projectLink) {
                    this.projectLink = changedProjectLink;
                }
            });
        }
    }

    protected entityInitialized() {
        // add logic when entity details are loaded.
    }

    protected routeParamsReceived(params) {
        // do something with the route params.
    }

    protected onProjectChange() {
        // do something special when project is changed
    }

    ngOnInit() {
        super.ngOnInit();

        this.routeParamsSubscription = this.route.params.subscribe(params => {
            this.id = params['id'];

            if (!this.id) {
                // no need to retrieve data
                this.routeParamsReceived(params);
                return;
            }

            this.service.getById(this.link, this.id).then(service => {
                this.entity = service;
                this.entityInitialized();

            }).catch(error => {
                console.error('Failed loading entity ', error);

                if (this.errorService) {
                    this.errorService.error(Utils.getErrorMessage(error)._generic);
                }
           });

       this.routeParamsReceived(params);
    });

    // try with the parent
    this.routeParentParamsSubscription = this.route.parent.params.subscribe(params => {
        this.id = params['id'];

        if (!this.id) {
            // no need to retrieve data
            return;
        }

        this.service.getById(this.link, this.id).then(service => {
            this.entity = service;
            this.entityInitialized();

        }).catch(error => {
            console.error('Failed loading parent entity ', error);

            if (this.errorService) {
                this.errorService.error(Utils.getErrorMessage(error)._generic);
            }
        });
    });
  }

    ngOnDestroy() {
        super.ngOnDestroy();

        this.routeParamsSubscription.unsubscribe();
        this.routeParentParamsSubscription.unsubscribe();
    }
}
