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
import { BaseDetailsComponent } from '../../../components/base/base-details.component';
import { Constants } from '../../../utils/constants';
import { DocumentService } from '../../../utils/document.service';
import { FT } from "../../../utils/ft";
import { Links } from '../../../utils/links';
import { Utils } from "../../../utils/utils";
import * as I18n from 'i18next';

@Component({
  selector: 'app-cluster-edit',
  templateUrl: './cluster-edit.component.html',
  styleUrls: ['./cluster-edit.component.scss'],
  encapsulation: ViewEncapsulation.None,
})
/**
 * Modal for editing clusters.
 */
export class ClusterEditComponent extends BaseDetailsComponent
                                    implements AfterViewInit, OnInit, OnDestroy {
  opened: boolean = false;
  credentials: any[];

  showCertificateWarning: boolean;
  certificate: any;

  isSaving: boolean;
  alertMessage: string;
  alertType: string;

  isVerifyingHost: boolean;
  isHostVerified: boolean = false;
  isSavingHost: boolean;

  private sub: any;
  private isSingleHostCluster: boolean = false;

  clusterForm = new FormGroup({
    name: new FormControl('', Validators.required),
    description: new FormControl(''),
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

  constructor(private router: Router, route: ActivatedRoute, service: DocumentService) {
    super(route, service, Links.CLUSTERS);
  }

  get title() {
    if (FT.isVic()) {
      return 'clusters.edit.titleEditVic';
    }
    return 'clusters.edit.titleEdit';
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

  get isVch(): boolean {
    return this.entity && this.entity.type === 'VCH';
  }

  entityInitialized() {
    this.isSingleHostCluster = Utils.isSingleHostCluster(this.entity);

    this.clusterForm.get('name').setValue(this.entity.name);
    if (this.entity.details) {
      this.clusterForm.get('description').setValue(this.entity.details);
    }
    if (this.entity.address) {
      this.clusterForm.get('url').setValue(this.entity.address);
    }

    // populate the credentials if the edited cluster is of type VCH
    if (this.isVch && this.entity.nodeLinks && this.entity.nodeLinks.length > 0) {
      var vchHost = this.entity.nodes[this.entity.nodeLinks[0]];
      let authCredentialsLink = Utils.getCustomPropertyValue(vchHost.customProperties, '__authCredentialsLink');
      if (authCredentialsLink) {
        var credItem = this.credentials.filter((c) => c.documentSelfLink === authCredentialsLink);
        if (credItem.length > 0) {
          this.clusterForm.get('credentials').setValue(credItem[0]);
        }
      }
    }

    if (this.isSingleHostCluster) {
      let publicAddress = this.entity.publicAddress || '';
      this.clusterForm.get('publicAddress').setValue(publicAddress);
    }
  }

  ngOnInit() {
    this.sub = this.route.params.subscribe(params => {
      let projectId = params['projectId'];
      if (projectId) {
        this.projectLink = Links.PROJECTS + '/' + projectId;
      }
      super.ngOnInit();
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
                            .map(Utils.toCredentialViewModel);
    }).catch((e) => {
      console.log('Credentials retrieval failed', e);
    });
  }

  toggleModal(open) {
    this.opened = open;

    if (!open) {
      const PATH_UP = '../../';

      let path: any[] = [PATH_UP];
      path = [PATH_UP + Utils.getDocumentId(this.entity.documentSelfLink)];

      this.router.navigate(path, { relativeTo: this.route });
    }
  }

  saveCluster() {
    let name = this.clusterForm.value.name;
    if (name) {
      let description = this.clusterForm.value.description;
      let clusterDtoPatch = {
        'name': name,
        'details':  description
      };

      // TODO check if the backend will handle this
      if (this.isSingleHostCluster) {
        // allow overwriting with empty value
        let publicAddress = this.clusterForm.value.publicAddress || '';
        clusterDtoPatch[Constants.clusters.properties.publicAddress] = publicAddress;
      }

      this.isSaving = true;
      this.service.patch(this.entity.documentSelfLink, clusterDtoPatch, this.projectLink)
        .then(() => {
        // hide modal
        this.toggleModal(false);

      }).catch(error => {
        this.isSaving = false;
        this.alertMessage = Utils.getErrorMessage(error)._generic;
      });
    }
  }

  getInputHost() {
    var vchHost = this.entity.nodes[this.entity.nodeLinks[0]];
    var hostCopy = Object.assign({}, vchHost);
    hostCopy.customProperties = Object.assign({}, vchHost.customProperties);

    let formInput = this.clusterForm.value;

    if (formInput.name) {
        hostCopy.customProperties['__hostAlias'] = formInput.name;
    }

    if (formInput.credentials) {
        hostCopy.customProperties['__authCredentialsLink'] =
                                                        formInput.credentials.documentSelfLink;
    }
    hostCopy.customProperties['__adapterDockerType'] = 'API';

    // allow overwriting with empty value
    hostCopy.customProperties[Constants.hosts.customProperties.publicAddress] =
        formInput.publicAddress || "";

    if (formInput.deploymentPolicy) {
        hostCopy.customProperties[Constants.hosts.customProperties.deploymentPolicyLink] =
                formInput.deploymentPolicy.documentSelfLink;
    } else {
        delete hostCopy.customProperties[Constants.hosts.customProperties.deploymentPolicyLink];
    }

    return hostCopy;
  }

  verifyCluster() {
    if (this.clusterForm.valid) {
        this.isVerifyingHost = true;
        this.isHostVerified = false;

        let host = this.getInputHost();
        let hostSpec = {
            'hostState': host
        };

        this.service.put(Links.CONTAINER_HOSTS + '?validate=true', hostSpec)
        .then((response) => {
            this.isVerifyingHost = false;
            this.isHostVerified = true;

            this.alertType = Constants.alert.type.SUCCESS;
            this.alertMessage = I18n.t('hosts.verified');
        }).catch(error => {
            this.isVerifyingHost = false;

            this.alertType = Constants.alert.type.DANGER;
            this.alertMessage = Utils.getErrorMessage(error)._generic;
        });
    }
  }

  clearView() {
    this.resetAlert();

    this.isSavingHost = false;
    this.isVerifyingHost = false;
    this.isHostVerified = true;

    this.clusterForm.reset();
    this.clusterForm.markAsPristine();
 }

  updateCluster() {
    if (this.clusterForm.valid) {
        this.isSavingHost = true;

        var hostState = this.getInputHost();
        let hostSpec = {
            'hostState': hostState,
            'isUpdateOperation': true
        };

        this.service.put(Links.CONTAINER_HOSTS, hostSpec)
        .then((response) => {
            this.clearView();

            this.toggleModal(false);
        }).catch(error => {
            this.isSavingHost = false;

            this.alertType = Constants.alert.type.DANGER;
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
  }

  resetAlert() {
    this.alertMessage = null;
  }
}
