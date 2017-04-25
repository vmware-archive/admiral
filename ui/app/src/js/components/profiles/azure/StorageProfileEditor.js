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
    <storage-item
    :storage-item="item"
    :index="index"
    @change="onStorageItemChange"
    @remove="onRemoveStorageItem">
    </storage-item>
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

Vue.component('storage-item', {
  template: `
    <div class="align-right toolbar">
      <a @click="onRemoveItem" class="btn btn-circle-outline">
        <i class="fa fa-minus"></i>
      </a>
    </div>
    <text-group
      :label="i18n('app.profile.edit.nameLabel')"
      :value="storageItem.name"
      :required="true"
      @change="onNameChange">
    </text-group>
    <multicolumn-editor-group
      :headers="[
      i18n('app.profile.edit.nameLabel'),
      i18n('app.profile.edit.valueLabel')
      ]"
      :label="i18n('app.profile.edit.diskPropertyMappingLabel')"
      :value="diskPropertyMapping"
      @change="onDiskPropertyChange">
      <multicolumn-cell name="name">
        <text-control></text-control>
      </multicolumn-cell>
      <multicolumn-cell name="value">
        <text-control></text-control>
      </multicolumn-cell>
    </multicolumn-editor-group>
    <div class="form-group">
      <label>{{i18n('app.profile.edit.defaultLabel')}}</label>
      <div class="radio">
        <input type="radio" name="defaultRadio" id="default-radio-{{index}}"
        :checked="storageItem.defaultItem" @click="onDefaultChange">
        <label for="default-radio-{{index}}">
          {{i18n('app.profile.edit.makeDescriptorDefault')}}</label>
      </div>
    </div>
    <tags-group
      :label="i18n('app.profile.edit.tagsLabel')"
      :hint="i18n('app.profile.edit.tagsHint')"
      :placeholder="i18n('app.profile.edit.tagsPlaceholder')"
      :value="tags"
      @change="onTagsChange">
    </tags-group>
  `,
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
    this.storageItem.tags = [];
    return {
      diskPropertyMapping: Object.keys(diskProperties).map((key) => {
        return {
          name: key,
          value: diskProperties[key]
        };
      }),
      tagLinks: tagLinks,
      tags: []
    };
  },
  methods: {
    onDiskPropertyChange(diskPropertyArray) {
      this.storageItem.diskProperties = diskPropertyArray.reduce((propertyMap, diskProperty) => {
        return Object.assign(propertyMap, {
          [diskProperty.name]: diskProperty.value
        });
      }, {});
    },
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
    }
  }
});
