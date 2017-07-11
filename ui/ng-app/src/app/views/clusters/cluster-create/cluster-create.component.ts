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

import { Component, OnInit, AfterViewInit, ViewEncapsulation } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { Links } from '../../../utils/links';
import { Utils } from "../../../utils/utils";
import { DocumentService } from '../../../utils/document.service';
import { ProjectService } from '../../../utils/project.service';
import * as I18n from 'i18next';

import { BaseDetailsComponent } from '../../../components/base/base-details.component';

@Component({
  selector: 'app-cluster-create',
  templateUrl: './cluster-create.component.html',
  styleUrls: ['./cluster-create.component.scss'],
  encapsulation: ViewEncapsulation.None,
})
/**
 * Modal for cluster creation.
 */
export class ClusterCreateComponent extends BaseDetailsComponent implements AfterViewInit, OnInit {
  opened: boolean;
  isEdit: boolean;
  credentials: any[];

  showCertificateWarning: boolean;
  certificate: any;

  isSaving: boolean;
  alertMessage: string;

  clusterForm = new FormGroup({
    name: new FormControl('', Validators.required),
    description: new FormControl(''),
    type: new FormControl('VCH'),
    url: new FormControl('', Validators.required),
    credentials: new FormControl('')
  });

  credentialsTitle = I18n.t('dropdownSearchMenu.title', {
    ns: 'base',
    entity: I18n.t('app.credential.entity', {ns: 'base'})
  } as I18n.TranslationOptions );

  credentialsSearchPlaceholder = I18n.t('dropdownSearchMenu.searchPlaceholder', {
    ns: 'base',
    entity: I18n.t('app.credential.entity', {ns: 'base'})
  } as I18n.TranslationOptions );

  constructor(private router: Router, route: ActivatedRoute, service: DocumentService) {
    super(route, service, Links.CLUSTERS);
  }

  get title() {
    return this.isEdit ? "clusters.edit.titleEdit" : "clusters.edit.titleNew";
  }

  entityInitialized() {
    this.isEdit = true;
    this.clusterForm.get('name').setValue(this.entity.name);
    if (this.entity.details) {
      this.clusterForm.get('description').setValue(this.entity.details);
    }
    this.clusterForm.get('type').setValue(this.entity.type);
    if (this.entity.address) {
      this.clusterForm.get('url').setValue(this.entity.address);
    }
  }

  ngAfterViewInit() {
    setTimeout(() => {
      this.opened = true;
      this.showCertificateWarning = false;
    });
    this.service.list(Links.CREDENTIALS, {}).then(credentials => {
      this.credentials = credentials.documents.map(this.toCredentialViewModel);
    });
  }

  toggleModal(open) {
    this.opened = open;
    if (!open) {
      let path: any[] = this.isEdit
                        ? ['../../' + Utils.getDocumentId(this.entity.documentSelfLink)] : ['../'];
      this.router.navigate(path, { relativeTo: this.route });
    }
  }

  toCredentialViewModel(credential) {
    let vm:any = {};
    vm.documentSelfLink = credential.documentSelfLink;
    vm.name = credential.customProperties ? credential.customProperties.__authCredentialsName : '';
    if (!vm.name) {
      vm.name = credential.documentId;
    }
    return vm;
  }

  saveCluster() {
    if (this.isEdit) {
      this.updateCluster();
    } else {
      this.createCluster(false);
    }
  }

  private updateCluster() {
    let name = this.clusterForm.value.name;
    if (name) {
      let description = this.clusterForm.value.description;
      let clusterDtoPatch = {
        'name': name,
        'details':  description
      };
      this.isSaving = true;
      this.service.patch(this.entity.documentSelfLink, clusterDtoPatch).then(() => {
        this.toggleModal(false);
      }).catch(error => {
        this.isSaving = false;
        this.alertMessage = Utils.getErrorMessage(error)._generic;
      });
    }
  }

  private createCluster(certificateAccepted: boolean) {
    if (this.clusterForm.valid) {
      this.isSaving = true;

      let formInput = this.clusterForm.value;
      let hostState = {
        'address': formInput.url,
        'customProperties': {
          '__containerHostType': formInput.type,
          '__adapterDockerType': 'API',
          '__clusterName': formInput.name
        }
      };

      if (formInput.credentials) {
        hostState.customProperties['__authCredentialsLink'] = formInput.credentials.documentSelfLink;
      }

      if (formInput.description) {
        hostState.customProperties['__clusterDetails'] = formInput.description;
      }

      let hostSpec = {
        'hostState': hostState,
        'acceptCertificate': certificateAccepted
      };
      this.service.post(Links.CLUSTERS, hostSpec).then((response) => {
        if (response.certificate) {
          this.certificate = response;
          this.showCertificateWarning = true;
        } else {
          this.isSaving = false;
          this.toggleModal(false);
        }
      }).catch(error => {
        this.isSaving = false;
        this.alertMessage = Utils.getErrorMessage(error)._generic;
      });
    }
  }

  cancelCreateCluster() {
    this.showCertificateWarning = false;
    this.isSaving = false;
  }

  acceptCertificate() {
    this.showCertificateWarning = false;
    this.createCluster(true);
  }

  resetAlert() {
    this.alertMessage = null;
  }

  saveButtonDisabled() {
    if (this.isSaving) {
      return true;
    }
    if (this.isEdit) {
      return !this.clusterForm.value.name;
    }
    return this.clusterForm.invalid;
  }
}