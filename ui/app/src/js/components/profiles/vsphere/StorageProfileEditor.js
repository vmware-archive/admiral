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
    :datastores="datastores"
    :index="index"
    @change="onStorageItemChange"
    @remove="onRemoveStorageItem">
    </vsphere-storage-item>
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
  created() {
    let datastores = [];
    if (this.endpoint && this.endpoint.documentSelfLink) {
      services.loadVsphereDatastores(this.endpoint.documentSelfLink).then((response) => {
        let resourceDocuments = utils.getDocumentArray(response);
        datastores = resourceDocuments.map((resourceDocument) => {
          return {
            name: resourceDocument.name,
            selfLink: resourceDocument.documentSelfLink
          };
        });
        this.datastores = datastores;
      });
    }
    this.datastores = datastores;
  },
  data() {
    let storageItems = this.model.storageItems &&
      this.model.storageItems.asMutable({deep: true}) || [];
    return {
      storageItems: storageItems,
      datastores: []
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
    },
    validate() {
      return this.storageItems.reduce((acc, storageItem) => {
        return acc && storageItem.valid;
      }, true);
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
    datastores: {
      required: true,
      type: Array
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
      datastore: diskProperties.datastore || '',
      sharesLevel: diskProperties.sharesLevel || SHARES_LEVEL_VALUES.normal,
      shares: diskProperties.shares || SHARES_VALUES.normal,
      limit: diskProperties.limit || ''
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
    this.onDiskPropertyChange('sharesLevel', SHARES_LEVEL_VALUES.normal);
    this.onDiskPropertyChange('shares', SHARES_VALUES.normal);
    this.onDiskPropertyChange('limit', '');
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
    onDefaultChange($event) {
      this.storageItem.defaultItem = $event.target.checked;
    },
    onDiskPropertyChange(diskPropertyName, value) {
      this.storageItem.diskProperties[diskPropertyName] = value;
      this.storageItem.valid = this.isValid();
      this.$emit('change');
    },
    onDatastoreChange($event) {
      this.onDiskPropertyChange('datastore', $event.target.value);
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
          break;
      }
      this.onDiskPropertyChange('sharesLevel', $event.target.value);
      this.onDiskPropertyChange('shares', this.shares);
    },
    onLimitChange($event) {
      this.onDiskPropertyChange('limit', $event.target.value);
    },
    onSharesChange($event) {
      this.shares = $event.target.value;
      this.onDiskPropertyChange('shares', this.shares);
    },
    isValid() {
      return !!this.storageItem.name && !!this.storageItem.diskProperties.datastore
        && (parseInt(this.storageItem.diskProperties.limit, 10) <= LIMIT_RANGE.max
        && parseInt(this.storageItem.diskProperties.limit, 10) >= LIMIT_RANGE.min
        || this.storageItem.diskProperties.limit === '')
        && parseInt(this.storageItem.diskProperties.shares, 10) <= SHARES_RANGE.max
        && parseInt(this.storageItem.diskProperties.shares, 10) >= SHARES_RANGE.min;
    }
  }
});
