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
import StorageItemVue from 'components/profiles/aws/StorageItemVue.html';

export default Vue.component('aws-storage-profile-editor', {
  template: `
    <div class="toolbar align-right">
    <a class="btn btn-circle new-item" @click="onAddStorageItem">
    <i class="fa fa-plus"></i></a>
    <a class="new-item" @click="onAddStorageItem">{{i18n('app.profile.edit.addStorageItem')}}</a>
  </div>
  <div v-for="(index, item) in storageItems" track-by="$index"
    class="storage-item"
    :class="index !== storageItems.length - 1 ? 'not-last-storage-item' : ''">
    <aws-storage-item
      :storage-item="item"
      :index="index"
      @change="onStorageItemChange"
      @remove="onRemoveStorageItem"
      @default-item-changed="onDefaultItemChange">
    </aws-storage-item>
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

const VOLUME_TYPES = [{
  name: i18n.t('app.profile.awsVolumeTypes.gp2'),
  value: 'gp2'
}, {
  name: i18n.t('app.profile.awsVolumeTypes.io1'),
  value: 'io1'
}, {
  name: i18n.t('app.profile.awsVolumeTypes.sc1'),
  value: 'sc1'
}, {
  name: i18n.t('app.profile.awsVolumeTypes.st1'),
  value: 'st1'
}, {
  name: i18n.t('app.profile.awsVolumeTypes.standard'),
  value: 'standard'
}];

const DEVICE_TYPES = [{
  name: i18n.t('app.profile.awsDeviceTypes.ebs'),
  value: 'ebs'
}, {
  name: i18n.t('app.profile.awsDeviceTypes.instanceStore'),
  value: 'instanceStore'
}];

const IOPS_LIMIT = 20000;

Vue.component('aws-storage-item', {
  template: StorageItemVue,
  props: {
    storageItem: {
      required: true,
      type: Object
    },
    index: {
      required: true,
      type: Number
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
  data() {
    let diskProperties = this.storageItem.diskProperties;
    let tagLinks = this.storageItem.tagLinks;
    if (!this.storageItem.name) {
      this.storageItem.name = `${i18n.t('app.profile.edit.itemHeader')} ${this.index}`;
    }
    return {
      volumeTypes: VOLUME_TYPES,
      volumeType: diskProperties.volumeType || '',
      deviceTypes: DEVICE_TYPES,
      deviceType: diskProperties.deviceType || '',
      iops: diskProperties.iops || '',
      tagLinks: tagLinks,
      tags: []
    };
  },
  computed: {
    ebsSelected: function() {
      if (this.deviceType) {
        return this.deviceType === 'ebs';
      }
      return false;
    },
    io1Selected: function() {
      return this.volumeType === 'io1';
    }
  },
  attached() {
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
    onVolumeTypeChange($event) {
      this.onDiskPropertyChange('volumeType', $event.target.value);
    },
    onDeviceTypeChange($event) {
      this.onDiskPropertyChange('deviceType', $event.target.value);
    },
    onIOPSChange($event) {
      this.onDiskPropertyChange('iops', $event.target.value);
    },
    onEncryptionChange(value) {
      this.storageItem.supportsEncryption = value;
    },
    isValid() {
      if (!this.deviceType && !this.name) {
        return false;
      } else {
        if (this.ebsSelected) {
          if (this.io1Selected) {
            return parseInt(this.iops, 10) <= IOPS_LIMIT;
          } else {
            return !!this.volumeType;
          }
        }
        return true;
      }
    }
  }
});
