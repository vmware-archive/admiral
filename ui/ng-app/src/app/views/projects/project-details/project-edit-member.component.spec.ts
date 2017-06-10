/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ProjectEditMemberComponent } from './project-edit-member.component';

describe('ProjectMembersComponent', () => {
    let component: ProjectEditMemberComponent;
    let fixture: ComponentFixture<ProjectEditMemberComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [ ProjectEditMemberComponent ]
        })
        .compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ProjectEditMemberComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
