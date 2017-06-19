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

import { Component, OnInit, AfterViewInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { Links } from '../../../utils/links';
import { Utils } from "../../../utils/utils";
import { DocumentService } from '../../../utils/document.service';
import * as I18n from 'i18next';

@Component({
  selector: 'app-cluster-create',
  templateUrl: './cluster-create.component.html',
  styleUrls: ['./cluster-create.component.scss']
})
/**
 * Modal for cluster creation.
 */
export class ClusterCreateComponent implements AfterViewInit, OnInit {
  opened: boolean;
  isEdit: boolean;
  selectedCredentials: any;
  credentials: any[];

  showCertificateWarning: boolean;
  certificate: any;
  certificateShown: boolean;
  certificateAccepted: boolean;

  isSaving: boolean;
  alertMessage: string;

  clusterForm = new FormGroup({
    name: new FormControl('', Validators.required),
    description: new FormControl(''),
    type: new FormControl('VCH'),
    url: new FormControl('', Validators.required),
    credentials: new FormControl('')
  });

  constructor(private router: Router, private route: ActivatedRoute, private service: DocumentService) { }

  ngOnInit() {
    this.route.queryParams.subscribe(queryParams => {
      this.service.list(Links.CREDENTIALS, queryParams).then(credentials => {
        this.credentials = credentials.documents;
      });
    });
  }

  ngAfterViewInit() {
    setTimeout(() => {
      this.opened = true;
      this.showCertificateWarning = false;
    });
  }

  toggleModal(open) {
    this.opened = open;
    if (!open) {
      this.router.navigate(['../'], { relativeTo: this.route });
    }
  }

  getCredentialsName(credentials) {
    let name = credentials.customProperties ? credentials.customProperties.__authCredentialsName : '';
    if (!name) {
      return credentials.documentId;
    }
    return name;
  }

  saveCluster() {
    if (this.clusterForm.valid) {
      this.isSaving = true;

      let formInput = this.clusterForm.value;
      let hostState = {
        'address': formInput.url,
        'tenantLinks': [Links.PROJECTS + '/default-project'],
        'customProperties': {
          '__containerHostType': formInput.type,
          '__adapterDockerType': 'API'
        }
      };

      if (formInput.credentials) {
        hostState.customProperties['__authCredentialsLink'] = formInput.credentials;
      }

      let hostSpec = {
        'hostState': hostState,
        'acceptCertificate': this.certificateAccepted
      };
      this.service.post(Links.CLUSTERS, hostSpec).then((response) => {
        if (response.certificate) {
          this.showCertificateWarning = true;
          this.certificate = response;
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

  certificateWarningMessage() {
    if (this.certificate) {
      return I18n.t("certificate.certificateWarning", {address: this.clusterForm.value.url} as I18n.TranslationOptions);
    }
    return '';
  }

  showCertificate() {
    this.certificateShown = true;
  }

  hideCertificate() {
    this.certificateShown = false;
  }

  acceptCertificate() {
    this.showCertificateWarning = false;
    this.certificateAccepted = true;
    this.saveCluster();
  }

  resetAlert() {
    this.alertMessage = null;
  }
}