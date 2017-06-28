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
import StorageItemVue from 'components/profiles/azure/StorageItemVue.html';

export default Vue.component('azure-storage-profile-editor', {
  template: `
  <div class="toolbar align-right">
    <a class="btn btn-circle new-item" @click="onAddStorageItem">
    <i class="fa fa-plus"></i></a>
    <a class="new-item" @click="onAddStorageItem">{{i18n('app.profile.edit.addStorageItem')}}</a>
  </div>
  <div v-for="(index, item) in storageItems" track-by="$index"
    class="storage-item"
    :class="index !== storageItems.length - 1 ? 'not-last-storage-item' : ''">
    <azure-storage-item
      :storage-item="item"
      :index="index"
      @change="onStorageItemChange"
      @remove="onRemoveStorageItem"
      @default-item-changed="onDefaultItemChange">
    </azure-storage-item>
  </div>
  `,
  props: {
    endpoint: {
      required: false,
      type: Object
    },
    model: {
      required: true,
      type: Object
    }
  },
  data() {
    let storageItems = this.model.storageItemsExpanded &&
      this.model.storageItemsExpanded.asMutable({deep: true}) || [];
    return {
      storageItemsSize: this.model.storageItems && this.model.storageItems.length || 0,
      storageItems: storageItems
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
    onDefaultItemChange(index) {
      this.storageItems.forEach((item, currentIndex) => {
        item.defaultItem = index === currentIndex;
      });
    },
    validate() {
      return this.storageItems.reduce((acc, storageItem) => {
        return acc && storageItem.valid;
      }, true);
    }
  }
});

const DISK_CACHING_TYPES = [{
  name: i18n.t('app.profile.azureDiskCachingType.none'),
  value: 'None'
}, {
  name: i18n.t('app.profile.azureDiskCachingType.readOnly'),
  value: 'ReadOnly'
}, {
  name: i18n.t('app.profile.azureDiskCachingType.readWrite'),
  value: 'ReadWrite'
}];

Vue.component('azure-storage-item', {
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
    disabled: {
      default: false,
      required: false,
      type: Boolean
    }
  },
  created() {
    if (this.tagLinks.length) {
      services.loadTags(this.tagLinks).then((tagsResponse) => {
        let tagsData = Object.values(tagsResponse);
        this.tags = this.storageItem.tags = tagsData.map(({key, value}) => ({
          key,
          value
        }));
      });
    }
  },
  attached() {
    this.storageItem.valid = this.isValid();
    this.$emit('change');
  },
  data() {
    let diskProperties = this.storageItem.diskProperties;
    let tagLinks = this.storageItem.tagLinks;
    this.storageItem.tags = [];
    if (!this.storageItem.name) {
      this.storageItem.name = `${i18n.t('app.profile.edit.itemHeader')} ${this.index}`;
    }
    return {
      osDiskCaching: diskProperties.azureOsDiskCaching || '',
      dataDiskCaching: diskProperties.azureDataDiskCaching || '',
      tagLinks: tagLinks,
      tags: [],
      cachingTypes: DISK_CACHING_TYPES
    };
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
    onOSDiskCachingChange($event) {
      this.onDiskPropertyChange('azureOsDiskCaching', $event.target.value);
    },
    onDataDiskCachingChange($event) {
      this.onDiskPropertyChange('azureDataDiskCaching', $event.target.value);
    },
    onDiskPropertyChange(diskPropertyName, value) {
      this.storageItem.diskProperties[diskPropertyName] = value;
      this.storageItem.valid = this.isValid();
      this.$emit('change');
    },
    renderStorageAccount(storageAccount) {
      let props = [
        i18n.t('app.profile.edit.storage.azure.typeLabel') + ': ' +
          utils.escapeHtml(storageAccount.type),
        i18n.t('app.profile.edit.storage.azure.encryptionLabel') + ': ' +
          utils.escapeHtml(storageAccount.supportsEncryption)
      ];
      let secondary = props.join(', ');
      return `
        <div>
          <div class="host-picker-item-primary">
            ${utils.escapeHtml(storageAccount.name)}
        </div>
        <div class="host-picker-item-secondary" title="${secondary}">
            ${secondary}
        </div>`;
    },
    searchAzureAccounts(filterString) {
      return new Promise((resolve, reject) => {
        services.loadAzureStorageAccounts(filterString).then((response) => {
          let result = {
            totalCount: response.totalCount
          };
          result.items = utils.getDocumentArray(response);
          resolve(result);
        }).catch(reject);
      });
    },
    onChange(value) {
      this.storageItem.storageDescriptionLink = value && value.documentSelfLink || '';
      this.storageItem.valid = this.isValid();
      this.$emit('change');
    },
    isValid() {
      return this.storageItem.name &&
        this.storageItem.storageDescriptionLink &&
        this.storageItem.diskProperties.azureOsDiskCaching &&
        this.storageItem.diskProperties.azureDataDiskCaching;
    }
  }
});
