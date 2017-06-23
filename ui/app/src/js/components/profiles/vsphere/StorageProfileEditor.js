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

import services from 'core/services';
import utils from 'core/utils';
import StorageItemVue from 'components/profiles/vsphere/StorageItemVue.html';

export default Vue.component('vsphere-storage-profile-editor', {
  template: `
  <div class="toolbar align-right">
    <a class="btn btn-circle new-item" @click="onAddStorageItem">
    <i class="fa fa-plus"></i></a>
    <a class="new-item" @click="onAddStorageItem">{{i18n('app.profile.edit.addStorageItem')}}</a>
  </div>
  <div v-for="(index, item) in storageItems" track-by="$index"
    class="storage-item"
    :class="index !== storageItems.length - 1 ? 'not-last-storage-item' : ''">
    <vsphere-storage-item
      :storage-item="item"
      :endpoint="endpoint"
      :index="index"
      :resource-group-state="storageItemsExpanded &&
        storageItemsExpanded[$index] &&
        storageItemsExpanded[$index].resourceGroupState || {}"
      :storage-description="storageItemsExpanded &&
        storageItemsExpanded[$index] &&
        storageItemsExpanded[$index].storageDescription || {}"
      @change="onStorageItemChange"
      @remove="onRemoveStorageItem"
      @default-item-changed="onDefaultItemChange">
    </vsphere-storage-item>
  </div>
  `,
  props: {
    endpoint: {
      required: true,
      type: Object
    },
    model: {
      required: true,
      type: Object
    }
  },
  data() {
    let storageItems = this.model.storageItems &&
      this.model.storageItems.asMutable({deep: true}) || [];
    let storageItemsExpanded = this.model.storageItemsExpanded;
    return {
      storageItemsSize: this.model.storageItems && this.model.storageItems.length || 0,
      storageItems: storageItems,
      storageItemsExpanded: storageItemsExpanded
    };
  },
  attached() {
    this.emitChange();
  },
  methods: {
    onStorageItemChange() {
      this.emitChange();
    },
    emitChange() {
      this.$emit('change', {
        properties: {
          storageItems: this.storageItems
        },
        valid: this.validate()
      });
    },
    onAddStorageItem() {
      let newStorageItem = {
        name: `Storage Item ${this.storageItemsSize++}`,
        tagLinks: [],
        diskProperties: {},
        defaultItem: !this.storageItems.length
      };
      this.storageItems.push(newStorageItem);
    },
    onRemoveStorageItem(index) {
      let isDefault = this.storageItems[index].defaultItem;
      this.storageItems.splice(index, 1);
      if (this.storageItems.length && isDefault) {
        this.storageItems[0].defaultItem = true;
      }
      this.emitChange();
    },
    validate() {
      return this.storageItems.reduce((acc, storageItem) => {
        return acc && storageItem.valid;
      }, true);
    },
    onDefaultItemChange(index) {
      this.storageItems.forEach((item, currentIndex) => {
        item.defaultItem = index === currentIndex;
      });
    }
  }
});

const PROVISIONTNG_TYPES = [{
  name: i18n.t('app.profile.vsphereProvisioningTypes.thin'),
  value: 'thin'
}, {
  name: i18n.t('app.profile.vsphereProvisioningTypes.thick'),
  value: 'thick'
}, {
  name: i18n.t('app.profile.vsphereProvisioningTypes.eagerZeroedThick'),
  value: 'eagerZeroedThick'
}];

const SHARES_LEVEL = [{
  name: i18n.t('app.profile.vsphereSharesLevel.low'),
  value: 'low'
}, {
  name: i18n.t('app.profile.vsphereSharesLevel.normal'),
  value: 'normal'
}, {
  name: i18n.t('app.profile.vsphereSharesLevel.high'),
  value: 'high'
}, {
  name: i18n.t('app.profile.vsphereSharesLevel.custom'),
  value: 'custom'
}];

const SHARES_VALUES = {
  low: 500,
  normal: 1000,
  high: 2000
};

const SHARES_LEVEL_VALUES = {
  low: 'low',
  normal: 'normal',
  high: 'high',
  custom: 'custom'
};

const LIMIT_RANGE = {
  max: 2147483647,
  min: 16
};

const SHARES_RANGE = {
  max: 4000,
  min: 200
};

Vue.component('vsphere-storage-item', {
  template: StorageItemVue,
  props: {
    storageItem: {
      required: true,
      type: Object
    },
    index: {
      required: true,
      type: Number
    },
    endpoint: {
      required: true,
      type: Object
    },
    resourceGroupState: {
      required: false,
      type: Object
    },
    storageDescription: {
      required: true,
      type: Object
    }
  },
  data() {
    let tagLinks = this.storageItem.tagLinks;
    let diskProperties = this.storageItem.diskProperties;
    if (!this.storageItem.name) {
      this.storageItem.name = `${i18n.t('app.profile.edit.itemHeader')} ${this.index}`;
    }
    return {
      tagLinks: tagLinks,
      tags: [],
      provisioningTypes: PROVISIONTNG_TYPES,
      sharesLevelTypes: SHARES_LEVEL,
      provisioningType: diskProperties.provisioningType || 'thin',
      sharesLevel: diskProperties.sharesLevel || SHARES_LEVEL_VALUES.normal,
      shares: diskProperties.shares || SHARES_VALUES.normal,
      isSharesValid: true,
      sharesInvalidMsg: '',
      limitIops: diskProperties.limitIops || '',
      isLimitIopsValid: true,
      limitIopsInvalidMsg: '',
      independent: diskProperties.independent === 'true'
    };
  },
  computed: {
    customShares: function() {
      return this.sharesLevel === SHARES_LEVEL_VALUES.custom;
    }
  },
  created() {
    if (this.tagLinks.length) {
      services.loadTags(this.tagLinks).then((tagsResponse) => {
        let tagsData = Object.values(tagsResponse);
        this.tags = tagsData.map(({key, value}) => ({
          key,
          value
        }));
      });
    }
  },
  attached() {
    this.onDiskPropertyChange('sharesLevel',
      this.storageItem.diskProperties.sharesLevel || SHARES_LEVEL_VALUES.normal);
    this.onDiskPropertyChange('shares',
      this.storageItem.diskProperties.shares || SHARES_VALUES.normal);
    this.onDiskPropertyChange('limitIops',
      this.storageItem.diskProperties.limitIops || '');
    this.onDiskPropertyChange('provisioningType',
      this.storageItem.diskProperties.provisioningType || 'thin');
    this.onDiskPropertyChange('independent',
      this.storageItem.diskProperties.independent || false);
    this.storageItem.valid = this.isValid();
    this.$emit('change');
  },
  methods: {
    onTagsChange(tags) {
      this.storageItem.tags = tags;
      this.tags = tags;
    },
    onNameChange(value) {
      this.storageItem.name = value;
      this.storageItem.valid = this.isValid();
      this.$emit('change');
    },
    onRemoveItem() {
      this.$emit('remove', this.index);
    },
    onDefaultChange() {
      this.$emit('default-item-changed', this.index);
    },
    onDiskPropertyChange(diskPropertyName, value) {
      this.storageItem.diskProperties[diskPropertyName] = value;
      this.storageItem.valid = this.isValid();
      this.$emit('change');
    },
    onProvisioningTypeChange($event) {
      this.onDiskPropertyChange('provisioningType', $event.target.value);
    },
    onSharesLevelChange($event) {
      let value = $event.target.value;
      switch (value) {
        case SHARES_LEVEL_VALUES.low:
          this.shares = SHARES_VALUES.low;
          this.customShares = false;
          break;
        case SHARES_LEVEL_VALUES.normal:
          this.shares = SHARES_VALUES.normal;
          this.customShares = false;
          break;
        case SHARES_LEVEL_VALUES.high:
          this.shares = SHARES_VALUES.high;
          this.customShares = false;
          break;
        case SHARES_LEVEL_VALUES.custom:
          this.customShares = true;
          this.shares = '';
          break;
      }
      this.onDiskPropertyChange('sharesLevel', $event.target.value);
      this.onDiskPropertyChange('shares', this.shares);
    },
    onLimitChange($event) {
      this.onDiskPropertyChange('limitIops', $event.target.value);
    },
    onSharesChange($event) {
      this.shares = $event.target.value;
      this.onDiskPropertyChange('shares', this.shares);
    },
    isValid() {
      this.isSharesValid = parseInt(this.storageItem.diskProperties.shares, 10) <= SHARES_RANGE.max
        && parseInt(this.storageItem.diskProperties.shares, 10) >= SHARES_RANGE.min;
      this.isLimitIopsValid =
        parseInt(this.storageItem.diskProperties.limitIops, 10) <= LIMIT_RANGE.max
        && parseInt(this.storageItem.diskProperties.limitIops, 10) >= LIMIT_RANGE.min
        || this.storageItem.diskProperties.limitIops === '';

      this.sharesInvalidMsg = this.isSharesValid ? '' :
        i18n.t('app.profile.edit.validation.valueInRange', {
          min: SHARES_RANGE.min,
          max: SHARES_RANGE.max
        });
      this.limitIopsInvalidMsg = this.isLimitIopsValid ? '' :
        i18n.t('app.profile.edit.validation.valueInRange', {
          min: LIMIT_RANGE.min,
          max: LIMIT_RANGE.max
        });

      return !!(this.storageItem.name
        && this.isLimitIopsValid
        && this.isSharesValid
        && this.storageItem.storageDescriptionLink);
    },
    searchVsphereDatastores(filterString) {
      return new Promise((resolve, reject) => {
        services.loadVsphereDatastores(this.endpoint.documentSelfLink, filterString,
          this.storageItem.resourceGroupLink)
          .then((response) => {
            let result = {
              totalCount: response.totalCount
            };
            result.items = utils.getDocumentArray(response);
            resolve(result);
        }).catch(reject);
      });
    },
    renderDatastore(datastore) {
      let props = [
        i18n.t('app.profile.edit.storage.vsphere.datastore.typeLabel') + ': ' +
        utils.escapeHtml(datastore.type),
        i18n.t('app.profile.edit.storage.vsphere.datastore.capacityLabel') + ': ' +
        utils.escapeHtml(utils.convertToGigabytes(datastore.capacityBytes)) +
        i18n.t('app.profile.edit.storage.vsphere.datastore.capacityUnit')
      ];
      let secondary = props.join(', ');
      return `
        <div>
          <div class="host-picker-item-primary">
            ${utils.escapeHtml(datastore.name)}
        </div>
        <div class="host-picker-item-secondary" title="${secondary}">
            ${secondary}
        </div>`;
    },
    searchVsphereStoragePolicies(filterString) {
      return new Promise((resolve, reject) => {
        services.loadVsphereStoragePolicies(this.endpoint.documentSelfLink, filterString)
          .then((response) => {
            let result = {
              totalCount: response.totalCount
            };
            result.items = utils.getDocumentArray(response);
            resolve(result);
        }).catch(reject);
      });
    },
    renderStoragePolicy(storagePolicy) {
      let props = [
        i18n.t('app.profile.edit.storage.vsphere.storagePolicy.descriptionLabel')
        + ': ' + utils.escapeHtml(storagePolicy.desc)
      ];
      return `
      <div>
        <div class="host-picker-item-primary">
          ${utils.escapeHtml(storagePolicy.name)}
        </div>
        <div class="host-picker-item-secondary" title="${props}">
          ${props}
        </div>
      </div>`;
    },
    onDatastoreChange(value) {
      this.storageItem.storageDescriptionLink = value && value.documentSelfLink || '';
      this.storageDescription = value;
      this.storageItem.valid = this.isValid();
      this.$emit('change');
    },
    onStoragePolicyChange(value) {
      this.storageItem.resourceGroupLink = value && value.documentSelfLink || null;
      this.storageItem.storageDescriptionLink = null;
      this.storageDescription = null;
      this.resourceGroupState = value;
      this.storageItem.valid = this.isValid();
      this.$emit('change');
    },
    onIndependentChange(value) {
      this.onDiskPropertyChange('independent', value);
    }
  }
});
