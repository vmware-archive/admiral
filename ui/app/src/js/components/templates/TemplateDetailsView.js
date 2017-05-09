/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import TemplateDetailsViewVue from 'components/templates/TemplateDetailsViewVue.html';
import ListItemImageVue from 'components/templates/ListItemImageVue.html';
import ContainerDefinitionVue from 'components/templates/ContainerDefinitionVue.html';
import ClosureDefinitionVue from 'components/templates/ClosureDefinitionVue.html';
import KubernetesTemplateItem from 'components/templates/kubernetes/KubernetesTemplateItem';
import ContainerDefinitionForm from 'components/containers/ContainerDefinitionForm';
import InlineDeleteConfirmationTemplate from 'components/common/InlineDeleteConfirmationTemplate.html'; //eslint-disable-line
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin';
import ResourceGroupsMixin from 'components/templates/ResourceGroupsMixin';
import GridHolderMixin from 'components/common/GridHolderMixin';
import ActionConfirmationSupportMixin from 'components/common/ActionConfirmationSupportMixin';
import ConnectorMixin from 'components/templates/ConnectorMixin';
import ResourceConnectionsDataMixin from 'components/templates/ResourceConnectionsDataMixin';
import NetworkBox from 'components/networks/NetworkBox'; //eslint-disable-line
import VolumeBox from 'components/volumes/VolumeBox'; //eslint-disable-line
import NetworkDefinitionForm from 'components/networks/NetworkDefinitionForm'; //eslint-disable-line
import VolumeDefinitionForm from 'components/volumes/VolumeDefinitionForm'; //eslint-disable-line
import TemplateNewItemMenu from 'components/templates/TemplateNewItemMenu'; //eslint-disable-line
import KubernetesDefinitionForm from 'components/kubernetes/KubernetesDefinitionForm';
import { TemplateActions } from 'actions/Actions';
import utils from 'core/utils';
import ft from 'core/ft';
import constants from 'core/constants';


var TemplateDetailsView = Vue.extend({
  template: TemplateDetailsViewVue,
  props: {
    model: {
      required: true,
      type: Object
    }
  },
  data: function() {
    return {
      savingContainer: false,
      addingContainer: false,
      savingNetwork: false,
      savingVolume: false,
      savingKubernetes: false,
      disableSavingNetworkButton: true,
      disableSavingVolumeButton: true,
      editingTemplateName: false,
      templateName: '',
      networkType: 'bridge',
      kubernetesContent: ''
    };
  },
  computed: {
    contextExpanded: function() {
      return this.$parent.model.contextView && this.$parent.model.contextView.expanded;
    },
    buttonsDisabled: function() {
       return this.savingContainer || this.addingContainer || this.savingNetwork
                || this.savingVolume || this.savingKubernetes;
    },
    disableSavingKubernetesButton: function() {
       var content = this.kubernetesContent && this.kubernetesContent.trim();
      return !content;
    },
    networks: function() {
      var networks = this.model.templateDetails && this.model.templateDetails.listView.networks;
      return networks || [];
    },
    items: function() {
      var items = this.model.templateDetails && this.model.templateDetails.listView.items;
      return items || [];
    },
    networkLinks: function() {
      var networkLinks = this.model.templateDetails &&
          this.model.templateDetails.listView.networkLinks;
      return networkLinks || {};
    },
    volumes: function() {
      var volumes = this.model.templateDetails && this.model.templateDetails.listView.volumes;
      return volumes || [];
    },
    kubernetes: function() {
      var kubernetes = this.model.templateDetails && this.model.templateDetails.listView.kubernetes;
      return kubernetes || [];
    },
    volumeLinks: function() {
      var volumeLinks = this.model.templateDetails
                          && this.model.templateDetails.listView.volumeLinks;
      return volumeLinks || {};
    },
    searchSuggestions: function() {
      return constants.TEMPLATES.SEARCH_SUGGESTIONS;
    },
    contextClosureExpanded: function() {
      return this.model.contextView && this.model.contextView.expanded;
    },
    innerContextExpanded: function() {
      var activeItemData = this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.data;
      return activeItemData && activeItemData.contextView && activeItemData.contextView.expanded;
    },
    areClosuresAllowed: function() {
      return ft.areClosuresAllowed();
    },
    isKubernetesEnabled: function() {
      return ft.isKubernetesHostOptionEnabled();
    },
    templateHasKubernetesEntities: function() {
      return this.kubernetes.length > 0;
    },
    templateHasGenericEntities: function() {
      return this.items.length > 0 || this.networks.length > 0 || this.volumes.length > 0;
    }
  },
  events: {
    'disableNetworkSaveButton': function(disable) {
      this.disableSavingNetworkButton = disable;
    },
    'disableVolumeSaveButton': function(disable) {
      this.disableSavingVolumeButton = disable;
    }
  },
  components: {
    'container-template-item': {
      template: ContainerDefinitionVue,

      props: {
        model: {
          required: true,
          type: Object
        },
        editLinks: {
          required: false
        },
        templateId: {
          required: true,
          type: String
        },
        numberOfNetworks: {
          required: true,
          type: Number
        }
      },

      mixins: [DeleteConfirmationSupportMixin],

      attached: function() {

        $(this.$el).on('click', '.template-links .delete-inline-item-confirmation-cancel', (e) => {
          e.preventDefault();
          e.stopPropagation();

          var $deleteConfirmationHolder = $(e.currentTarget)
            .closest('.delete-inline-item-confirmation-holder');

            removeConfirmationHolder.call(this, $deleteConfirmationHolder);
        });

        $(this.$el).on('click', '.template-links .delete-inline-item-confirmation-confirm', (e) => {
          e.preventDefault();
          e.stopPropagation();

          let linkName = $(e.currentTarget).parents('.row').find('.link-service').text();
          let alias = $(e.currentTarget).parents('.row').find('.link-alias').text();

          let link = constructLink(linkName, alias);

          let containerDefinition = this.model.asMutable();
          let links = containerDefinition.links.asMutable();
          let ind = links.indexOf(link);
          if (ind > -1) {
            links.splice(ind, 1);
          }
          containerDefinition.links = links;

          TemplateActions.saveContainerDefinition(this.templateId, containerDefinition);

          this.model = this.model.merge(containerDefinition);
        });

        this.unwatchTemplateDetails = this.$watch('model.templateDetails', (templateDetails) => {
          this.templateName = templateDetails.name;
        });

        this.$dispatch('attached', this);
      },
      detached: function() {
        this.unwatchTemplateDetails();

        this.$dispatch('detached', this);
      },

      methods: {
        editContainerDefinition: function($event) {
          $event.preventDefault();
          TemplateActions.openEditContainerDefinition(this.model.documentSelfLink);
        },

        deleteContainerDefinition: function() {
          this.confirmRemoval(TemplateActions.removeContainerDefinition, [this.model]);
        },

        toggleAddLinkBtnPanel: function(show) {
          var $linkBtnParent = $(this.$el).find('.container-action-new-item-link').parent();

          if (show) {
            $linkBtnParent.show();
          } else {
            $linkBtnParent.hide();
          }
        },

        toggleNoLinksPanel: function(show) {
          var $noLinksEl = $(this.$el).find('.no-links');
          if (show) {
            if (!this.model.links || this.model.links.length === 0) {
              $noLinksEl.show();
            }
          } else {
            $noLinksEl.hide();
          }
        },

        addNewLink: function($event) {
          $event.preventDefault();

          $(this.$el).find('.links-form').show();
          $(this.$el).parent('.grid-item').addClass('active');

          this.toggleAddLinkBtnPanel(false);
          this.toggleNoLinksPanel(false);
        },

        saveContainerLink: function($event) {
          $event.preventDefault();

          let linkService = $(this.$el).find('select').val();
          let linkAlias = $(this.$el).find('input').val();
          if (!linkService) {
            return;
          }

          let links = [];
          let rows = $(this.$el).find('.template-links .row');
          for (let i = 0; i < rows.size(); i++) {
            links.push(constructLink($(rows[i]).find('.link-service').text(),
                                     $(rows[i]).find('.link-alias').text()));
          }

          let link = constructLink(linkService, linkAlias);
          links.push(link);

          if (this.editLinks) {
            let ind = links.indexOf(this.editLinks);
            if (ind > -1) {
              links.splice(ind, 1);
            }

            this.editLinks = null;
          }

          $(this.$el).find('select').prop('selectedIndex', 0);
          $(this.$el).find('input').val(null);

          var containerDefinition = this.model.asMutable();
          containerDefinition.links = links;

          TemplateActions.saveContainerDefinition(this.templateId, containerDefinition);

          $(this.$el).find('.row').show();
          $(this.$el).find('.links-form').hide();
          this.toggleAddLinkBtnPanel(true);
          $(this.$el).parent('.grid-item').removeClass('active');
        },

        cancelSaveContainerLink: function($event) {
          $event.preventDefault();
          $(this.$el).find('.links-form').hide();
          this.toggleAddLinkBtnPanel(true);
          this.toggleNoLinksPanel(true);
          $(this.$el).parent('.grid-item').removeClass('active');

          if (this.editLinks) {
            $(this.$el).find('.row').show();
            this.editLinks = null;

            $(this.$el).find('select').prop('selectedIndex', 0);
            $(this.$el).find('input').val(null);
          }
        },

        editContainerLink: function($event) {
          $event.preventDefault();

          $(this.$el).find('.links-form').show();
          $(this.$el).parent('.grid-item').addClass('active');

          let linkName = $($event.currentTarget).parents('.row').find('.link-service').text();
          let alias = $($event.currentTarget).parents('.row').find('.link-alias').text();

          $(this.$el).find('select').val(linkName);
          $(this.$el).find('input').val(alias);

          this.editLinks = linkName + ':' + alias;
          $($event.currentTarget).parents('.row').hide();
        },

        deleteContainerLink: function($event) {
          $event.preventDefault();

          var $row = $($event.currentTarget).parents('.template-links .row');

          var $deleteConfirmationHolder = $(InlineDeleteConfirmationTemplate());
          var $deleteConfirmation = $deleteConfirmationHolder
            .find('.delete-inline-item-confirmation');

          $deleteConfirmationHolder.height($row.outerHeight(true) + 1);
          $row.append($deleteConfirmationHolder);

          utils.slideToLeft($deleteConfirmation);
        },

        modifyClusterSize($event, incrementValue) {
          $event.preventDefault();

          if (!incrementValue) {
            return;
          }

          if (incrementValue < 0) {
            TemplateActions.decreaseClusterSize(this.model);
          } else {
            TemplateActions.increaseClusterSize(this.model);
          }
        }
      }
    },
    'closure-template-item': {
      props: {
        model: {
          required: true,
          type: Object
        },
        editLinks: {
          required: false
        },
        templateId: {
          required: true,
          type: String
        },
        numberOfNetworks: {
          required: true,
          type: Number
        }
      },
      computed: {
        closureIcon: function() {
          if (this.model.runtime.startsWith('nodejs')) {
            return 'image-assets/closure-nodejs.png';
          } else if (this.model.runtime.startsWith('python')) {
            return 'image-assets/closure-python.png';
          } else if (this.model.runtime.startsWith('powershell')) {
            return 'image-assets/closure-powershell.png';
          } else if (this.model.runtime.startsWith('java')) {
            return 'image-assets/closure-java.png';
          }
          return 'image-assets/closure-unknown.png';
        },
        closureRuntime: function() {
          if (this.model.runtime === 'nodejs') {
            return 'NodeJS 4';
          } else if (this.model.runtime === 'python') {
            return 'Python 3';
          } else if (this.model.runtime === 'powershell') {
            return 'Powershell 6';
          } else if (this.model.runtime === 'java') {
            return 'Java 8';
          }
          return 'Unknown';
        }
      },

      template: ClosureDefinitionVue,
      mixins: [DeleteConfirmationSupportMixin],

      attached: function() {

        $(this.$el).on('click', '.template-links .delete-inline-item-confirmation-cancel', (e) => {
          e.preventDefault();
          e.stopPropagation();

          var $deleteConfirmationHolder = $(e.currentTarget)
            .closest('.delete-inline-item-confirmation-holder');

            removeConfirmationHolder.call(this, $deleteConfirmationHolder);
        });

        $(this.$el).on('click', '.template-links .delete-inline-item-confirmation-confirm', (e) => {
          e.preventDefault();
          e.stopPropagation();

          let linkName = $(e.currentTarget).parents('.row').find('.link-service').text();
          let alias = $(e.currentTarget).parents('.row').find('.link-alias').text();

          let link = constructLink(linkName, alias);

          let containerDefinition = this.model.asMutable();
          let links = containerDefinition.links.asMutable();
          let ind = links.indexOf(link);
          if (ind > -1) {
            links.splice(ind, 1);
          }
          containerDefinition.links = links;

          TemplateActions.saveContainerDefinition(this.templateId, containerDefinition);

          this.model = this.model.merge(containerDefinition);
        });

        this.unwatchTemplateDetails = this.$watch('model.templateDetails', (templateDetails) => {
          this.templateName = templateDetails.name;
        });

        this.$dispatch('attached', this);
      },
      detached: function() {
        this.unwatchTemplateDetails();

        this.$dispatch('detached', this);
      },

      methods: {
        editClosureDescription: function(e) {
          if (e != null) {
              e.preventDefault();
          }

          TemplateActions.openAddClosure(this.model);
        },

        deleteClosureDescription: function() {
          TemplateActions.removeClosure(this.model, this.templateId);
        }
      }
    },
    'container-image-item': {
      template: ListItemImageVue,
      mixins: [ResourceGroupsMixin],
      props: {
        model: {required: true}
      },
      data: function() {
        return {
          readOnly: true
        };
      },
      attached: function() {
        $(this.$el).on('click', '.select-image-btn', () =>
                TemplateActions.selectImageForContainerDescription(this.model.documentId));
      },
      detached: function() {
      }
    },
    'container-definition-form': {
      template: '<div></div>',
      props: {
        model: {
          required: true,
          type: Object
        }
      },
      attached: function() {
        this.newDefinitionForm = new ContainerDefinitionForm();
        $(this.$el).append(this.newDefinitionForm.getEl());
        this.unwatchModel = this.$watch('model', (model) => {
          if (model) {
            this.newDefinitionForm.setData(model);

            var alertMessage = (model.error) ? model.error._generic : model.error;
            if (alertMessage) {
              this.$dispatch('container-form-alert',
                             alertMessage,
                             constants.ALERTS.TYPE.FAIL);
            }
          }
        }, {immediate: true});
      },
      detached: function() {
        this.unwatchModel();
      },
      methods: {
        getContainerDescription: function() {
          return this.newDefinitionForm.getContainerDescription();
        },
        getContainerDefinitionForm() {
          return this.newDefinitionForm;
        }
      }
    },
    'kubernetes-definition-form': KubernetesDefinitionForm,
    'kubernetes-template-item': KubernetesTemplateItem
  },
  mixins: [GridHolderMixin, ConnectorMixin, ResourceConnectionsDataMixin, ResourceGroupsMixin,
            ActionConfirmationSupportMixin],
  attached: function() {
    var $detailsContent = $(this.$el);

    $detailsContent.on('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd',
      (e) => {
        if (e.target === $detailsContent[0]) {
          this.unsetPostTransitionGridTargetWidth();
        }
      }
    );

    this.unwatchExpanded = this.$watch('contextExpanded', () => {
      Vue.nextTick(() => {
        this.setPreTransitionGridTargetWidth($detailsContent);
      });
    });

    this.unwatchModel = this.$watch('model', (model) => {
      this.savingContainer = false;
      this.addingContainer = false;
      this.savingNetwork = false;
      this.savingVolume = false;
      this.savingKubernetes = false;

      if (model.alert) {
        this.$dispatch('container-form-alert', model.alert.message, model.alert.type);
      } else {
        this.$dispatch('container-form-alert', null, null);
      }
    });

    this.bindResourceConnection(constants.RESOURCE_CONNECTION_TYPE.NETWORK,
                                  TemplateActions.attachNetwork);
    this.bindResourceDetachConnection(constants.RESOURCE_CONNECTION_TYPE.NETWORK,
                                        TemplateActions.detachNetwork);
    this.bindResourceAttachDetachConnection(constants.RESOURCE_CONNECTION_TYPE.NETWORK,
                                              TemplateActions.attachDetachNetwork);

    this.bindResourceConnection(constants.RESOURCE_CONNECTION_TYPE.VOLUME,
                                            TemplateActions.attachVolume);
    this.bindResourceDetachConnection(constants.RESOURCE_CONNECTION_TYPE.VOLUME,
                                            TemplateActions.detachVolume);
    this.bindResourceAttachDetachConnection(constants.RESOURCE_CONNECTION_TYPE.VOLUME,
                                            TemplateActions.attachDetachVolume);
  },
  detached: function() {
    this.unwatchExpanded();

    var $detailsContent = $(this.$el);
    $detailsContent.off('transitionend MSTransitionEnd webkitTransitionEnd oTransitionEnd');

    this.unwatchModel();
    if (this.model.alert) {
      this.$dispatch('container-form-alert', null, null);
    }
  },
  methods: {
    handleBackButton: function(e) {
      if (e) {
        e.preventDefault();
        e.stopImmediatePropagation();
      }
      var data = this.model || {};
      if (data.editNetwork) {
        TemplateActions.cancelEditNetwork();
        return true;
      }

      if (data.editVolume) {
        TemplateActions.cancelEditVolume();
        return true;
      }

      if (data.editKubernetes) {
        TemplateActions.cancelEditKubernetesDefinition();
        return true;
      }

      if (data.newContainerDefinition || data.editContainerDefinition) {
        if (data.newContainerDefinition && data.newContainerDefinition.definitionInstance) {
          TemplateActions.resetContainerDefinitionEdit();
        } else {
          TemplateActions.cancelContainerDefinitionEdit();
        }

        return true;
      }

      if (data.addClosureView) {
        TemplateActions.cancelAddClosure(this.model.documentId);
        return true;
      }

      return false;
    },
    addContainerDefinition: function() {
      var containerForm = this.$refs.newForm.getContainerDefinitionForm();
      var validationErrors = containerForm.validate();

      containerForm.applyValidationErrors(validationErrors);
      if (!validationErrors) {
        this.addingContainer = true;
        var containerDescription = containerForm.getContainerDescription();

        TemplateActions.addContainerDefinition(this.model.documentId, containerDescription);
      }
    },
    saveContainerDefinition: function() {
      var containerForm = this.$refs.editForm.getContainerDefinitionForm();
      var validationErrors = containerForm.validate();

      containerForm.applyValidationErrors(validationErrors);
      if (!validationErrors) {
        this.savingContainer = true;
        var containerDescription = containerForm.getContainerDescription();

        TemplateActions.saveContainerDefinition(this.model.documentId, containerDescription);
      }
    },
    openAddNewContainerDefinition: function() {
      TemplateActions.openAddNewContainerDefinition();
    },
    openAddNewNetwork: function() {
      TemplateActions.openEditNetwork();
      this.$dispatch('disableNetworkSaveButton', true);
    },
    openAddNewVolume: function() {
      TemplateActions.openEditVolume();

      this.$dispatch('disableVolumeSaveButton', true);
    },
    openAddNewKubernetes: function() {
      TemplateActions.openEditKubernetesDefinition();
    },
    openAddNewClosure: function() {
      TemplateActions.openAddClosure();
    },
    removeClosure: function(e) {
      TemplateActions.removeClosure(e.model, this.model.documentId);
    },
    editClosureDescription: function(e) {
      TemplateActions.openAddClosure(e.model);
    },

    saveNetwork: function($event) {
      $event.preventDefault();

      var networkForm = this.$refs.networkEditForm;
      var validationErrors = networkForm.validate();
      if (!validationErrors) {
        var network = networkForm.getNetworkDefinition();
        this.savingNetwork = true;
        TemplateActions.saveNetwork(this.model.documentId, network);
      }
    },

    saveVolume: function($event) {
      $event.preventDefault();

      var volumeForm = this.$refs.volumeEditForm;
      var validationErrors = volumeForm.validate();
      if (!validationErrors) {
        var volume = volumeForm.getVolumeDefinition();
        this.savingVolume = true;

        TemplateActions.saveVolume(this.model.documentId, volume);
      }
    },

    saveKubernetes: function($event) {
      $event.preventDefault();

      if (this.kubernetesContent) {
        var kubernetisEntity = this.model.editKubernetes.definitionInstance;

        this.savingKubernetes = true;
        TemplateActions.saveKubernetesDefinition(this.model.documentId,
          this.kubernetesContent, kubernetisEntity);
      }
    },

    // Search for image
    searchForImage: function(queryOptions) {
      TemplateActions.searchImagesForContainerDefinition(queryOptions);
    },
    // Template Name
    editTemplateName: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      this.editingTemplateName = true;
    },
    saveTemplateName: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      this.templateName = $(this.$el).find('#templateNameInput').val();
      this.editingTemplateName = false;

      TemplateActions.saveTemplateName(this.model.documentId, this.templateName);
    },
    cancelEditTemplateName: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      this.templateName = this.model.templateDetails.name;
      this.editingTemplateName = false;
    },
    // Actions on templates
    provision: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      if ($event.shiftKey) {
        this.showGroupForProvisioning = false;
      } else {
        var template = {
          'documentSelfLink': this.model.templateDetails.documentSelfLink
        };

        this.handleGroup(TemplateActions.copyTemplate, [this.model.type, template]);
      }
    },
    handleConfirmation: function(actionName) {
      // remove template
      if (actionName === 'removeTemplate') {
        TemplateActions.removeTemplate(this.model.documentId);
      }
    },
    publishTemplate: function($event) {
      $event.stopPropagation();
      $event.preventDefault();

      TemplateActions.publishTemplate(this.model.documentId);
    },

    exportTemplate: function() {
      this.$dispatch('export-template', this.model.documentId);
    },

    operationSupported: function(op) {
      var listView = this.model.templateDetails.listView;
      var hasContainers = listView.items && listView.items.length > 0;
      var hasKubernetes = listView.kubernetes && listView.kubernetes.length > 0;
      if (op === 'PROVISION' || op === 'PUBLISH' || op === 'EXPORT') {
        if (!hasContainers && !hasKubernetes) {
          return false;
        }
      }

      return utils.operationSupportedTemplate(op);
    },

    layoutComplete: function() {
      setTimeout(() => {
        this.onLayoutUpdate();
      }, 500);
    },
    kubernetesContentChange: function(content) {
      this.kubernetesContent = content;
    }
  }
});

var constructLink = function(linkName, alias) {
  return linkName + (alias ? ':' + alias : '');
};

var removeConfirmationHolder = function($deleteConfirmationHolder) {
  utils.fadeOut($deleteConfirmationHolder, function() {
    $deleteConfirmationHolder.remove();
  });
};

Vue.component('template-details-view', TemplateDetailsView);

export default TemplateDetailsView;

