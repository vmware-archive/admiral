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

import { ActivatedRoute, Router } from '@angular/router';
import { Component, OnInit } from '@angular/core';


@Component({
    selector: 'app-tag-details',
    templateUrl: './tag-details.component.html',
    styleUrls: ['./tag-details.component.scss']
})
export class TagDetailsComponent implements OnInit {

    tagId: string;
    repositoryId: string;

    constructor(private route: ActivatedRoute, private router: Router) {
    }

    ngOnInit() {
        this.repositoryId = this.route.snapshot.params['rid'];
        this.tagId = this.route.snapshot.params['tid'];
    }

    goBack(tag: string) {
        this.router.navigate(['../../../../'], {relativeTo: this.route});
    }

}
