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

import { Component, OnInit, OnDestroy, AfterViewInit, ViewEncapsulation } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormGroup, FormControl, Validators } from "@angular/forms";
import { Constants } from '../../../utils/constants';
import { DocumentService } from '../../../utils/document.service';
import { FT } from "../../../utils/ft";
import { Links } from '../../../utils/links';
import { Utils } from "../../../utils/utils";
import { formatUtils } from 'admiral-ui-common';
import * as I18n from 'i18next';

@Component({
  selector: 'app-cluster-create',
  templateUrl: './cluster-create.component.html',
  styleUrls: ['./cluster-create.component.scss'],
  encapsulation: ViewEncapsulation.None,
})
/**
 * Modal for cluster creation.
 */
export class ClusterCreateComponent implements AfterViewInit, OnInit, OnDestroy {
  opened: boolean = false;
  credentials: any[];
  projectLink: string;

  showCertificateWarning: boolean;
  certificate: any;

  isSaving: boolean;
  alertMessage: string;

  private sub: any;
  private isSingleHostCluster: boolean = false;

  clusterForm = new FormGroup({
    name: new FormControl('', Validators.required),
    description: new FormControl(''),
    type: new FormControl('VCH'),
    url: new FormControl('', Validators.required),
    publicAddress: new FormControl(''),
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

  constructor(private router: Router, private route: ActivatedRoute, private service: DocumentService) {}

  get title() {
    if (FT.isVic()) {
      return 'clusters.edit.titleNewVic';
    }
    return 'clusters.edit.titleNew';
  }

  get urlRequiredTextKey() {
    if (FT.isVic()) {
      return 'clusters.edit.urlRequiredVic'
    }
    return 'clusters.edit.urlRequired'
  }

  get showPublicAddressField(): boolean {
    return FT.isHostPublicUriEnabled() && this.isSingleHostCluster;
  }

  get isKubernetesHostOptionEnabled(): boolean {
    return FT.isKubernetesHostOptionEnabled();
  }

  ngOnInit() {
    this.sub = this.route.params.subscribe(params => {
      let projectId = params['projectId'];
      if (projectId) {
        this.projectLink = Links.PROJECTS + '/' + projectId;
      }
    });
    this.populateCredentials();
  }

  ngOnDestroy() {
    this.sub.unsubscribe();
  }

  ngAfterViewInit() {
    this.opened = true;
    this.showCertificateWarning = false;
  }

  populateCredentials() {
    if (this.credentials) {
      return;
    }

    this.service.list(Links.CREDENTIALS, {}).then(credentials => {
      this.credentials = credentials.documents
                          .filter(c => !Utils.areSystemScopedCredentials(c))
                            .map(this.toCredentialViewModel);
    }).catch((e) => {
      console.log('Credentials retrieval failed', e);
    });
  }

  toggleModal(open) {
    this.opened = open;

    if (!open) {
      const PATH_UP = '../../';
      const PROJECTS_VIEW_URL_PART = 'projects';
      const TAB_ID_PROJECT_INFRASTRUCTURE = 'infra';

      let path: any[] = [PATH_UP];
          // New cluster was created
          if (this.router.url.indexOf(PROJECTS_VIEW_URL_PART) > -1) {
            // New cluster was created in the Projects view
            if (this.router.url.indexOf(TAB_ID_PROJECT_INFRASTRUCTURE) < 0) {
              // Preselect infrastructure tab in project details view upon new cluster creation
              path = [PATH_UP + TAB_ID_PROJECT_INFRASTRUCTURE];
          }
      }

      this.router.navigate(path, { relativeTo: this.route });
    }
  }

  toCredentialViewModel(credential) {
    let credentialsViewModel:any = {};

    credentialsViewModel.documentSelfLink = credential.documentSelfLink;
    credentialsViewModel.name = credential.customProperties
                                    ? credential.customProperties.__authCredentialsName : '';
    if (!credentialsViewModel.name) {
      credentialsViewModel.name = credential.documentId;
    }

    return credentialsViewModel;
  }

  saveCluster() {
      this.createCluster(false);
  }

  private createCluster(certificateAccepted: boolean) {
    if (this.clusterForm.valid) {
      this.isSaving = true;

      let formInput = this.clusterForm.value;
      let clusterName = formInput.name && formatUtils.escapeHtml(formInput.name);
      let hostState = {
        'address': formInput.url,
        'customProperties': {
          '__containerHostType': formInput.type,
          '__adapterDockerType': 'API',
          '__clusterName': clusterName
        }
      };

      if (formInput.credentials) {
        hostState.customProperties['__authCredentialsLink'] = formInput.credentials.documentSelfLink;
      }

      if (formInput.description) {
        hostState.customProperties['__clusterDetails'] = formInput.description;
      }

      if (formInput.publicAddress) {
        hostState.customProperties[Constants.hosts.customProperties.publicAddress] = formInput.publicAddress;
      }

      let hostSpec = {
        'hostState': hostState,
        'acceptCertificate': certificateAccepted
      };
      this.service.post(Links.CLUSTERS, hostSpec, this.projectLink).then((response) => {
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
}
