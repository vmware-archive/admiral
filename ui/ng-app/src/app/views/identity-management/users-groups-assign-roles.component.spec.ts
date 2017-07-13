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

import { UsersGroupsAssignRolesComponent } from './users-groups-assign-roles.component';

describe('UsersGroupsAssignRolesComponent', () => {
    let component: UsersGroupsAssignRolesComponent;
    let fixture: ComponentFixture<UsersGroupsAssignRolesComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [ UsersGroupsAssignRolesComponent ]
        })
        .compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(UsersGroupsAssignRolesComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
