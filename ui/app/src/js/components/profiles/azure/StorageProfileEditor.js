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
    :storage-accounts="storageAccounts"
    @change="onStorageItemChange"
    @remove="onRemoveStorageItem">
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
    let storageItems = this.model.storageItems &&
      this.model.storageItems.asMutable({deep: true}) || [];
    return {
      storageItems: storageItems,
      storageAccounts: []
    };
  },
  created() {
    services.loadStorageAccounts().then((response) => {
      let documents = utils.getDocumentArray(response);
      let accountNames = documents.map((document) => {
        return document.name;
      });
      this.storageAccounts = accountNames || [];
    });
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
        valid: true
      });
    },
    onAddStorageItem() {
      let newStorageItem = {
        name: '',
        tagLinks: [],
        diskProperties: {},
        defaultForDisk: false
      };
      this.storageItems.push(newStorageItem);
    },
    onRemoveStorageItem(index) {
      this.storageItems.splice(index, 1);
      this.emitChange();
    }
  }
});

const STORAGE_ACCOUNT_TYPES = [{
  name: i18n.t('app.profile.azureStorageAccountType.standardLRS'),
  value: 'Standard_LRS'
}, {
  name: i18n.t('app.profile.azureStorageAccountType.standardZRS'),
  value: 'Standard_ZRS'
}, {
  name: i18n.t('app.profile.azureStorageAccountType.standardGRS'),
  value: 'Standard_GRS'
}, {
  name: i18n.t('app.profile.azureStorageAccountType.standardRAGRS'),
  value: 'Standard_RAGRS'
}, {
  name: i18n.t('app.profile.azureStorageAccountType.premiumLRS'),
  value: 'Premium_LRS'
}];

const OS_DISK_CACHING_TYPES = [{
  name: i18n.t('app.profile.azureOSDiskCachingType.none'),
  value: 'None'
}, {
  name: i18n.t('app.profile.azureOSDiskCachingType.readOnly'),
  value: 'ReadOnly'
}, {
  name: i18n.t('app.profile.azureOSDiskCachingType.readWrite'),
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
    storageAccounts: {
      required: true,
      type: Array
    }
  },
  created() {
    if (this.tagLinks.length) {
      services.loadTags(this.tagLinks).then((tagsResponse) => {
        let tagsData = Object.values(tagsResponse);
        this.storageItem.tags = tagsData.map(({key, value}) => ({
          key,
          value
        }));
      });
    }
  },
  data() {
    let diskProperties = this.storageItem.diskProperties;
    let tagLinks = this.storageItem.tagLinks;
    this.storageItem.tags = [];
    if (!this.storageItem.name) {
      this.storageItem.name = `${i18n.t('app.profile.edit.itemHeader')} ${this.index}`;
    }
    return {
      storageAccount: diskProperties.azureStorageAccountName || '',
      storageAccountType: diskProperties.azureStorageAccountType || '',
      osDiskCaching: diskProperties.azureOsDiskCaching || '',
      tagLinks: tagLinks,
      tags: [],
      accountTypes: STORAGE_ACCOUNT_TYPES,
      cachingTypes: OS_DISK_CACHING_TYPES,
      existingAccountSelected: true
    };
  },
  methods: {
    onTagsChange(tags) {
      this.storageItem.tags = tags;
      this.tags = tags;
    },
    onNameChange(value) {
      this.storageItem.name = value;
    },
    onRemoveItem() {
      this.$emit('remove', this.index);
    },
    onDefaultChange($event) {
      this.storageItem.defaultItem = $event.target.checked;
    },
    onAccountNameChange($event) {
      let value = $event.target.value;
      this.existingAccountSelected = this.storageAccounts.find((storageAccount) => {
        return storageAccount === value;
      });
      this.onDiskPropertyChange('azureStorageAccountName', value);
    },
    onAccountTypeChange($event) {
      this.onDiskPropertyChange('azureStorageAccountType', $event.target.value);
    },
    onOSDiskCachingChange($event) {
      this.onDiskPropertyChange('azureOsDiskCaching', $event.target.value);
    },
    onDiskPropertyChange(diskPropertyName, value) {
      this.storageItem.diskProperties[diskPropertyName] = value;
    }
  }
});
