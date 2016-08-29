var ResourceGroupsMixin = {
  computed: {
    hasGroups: function() {
      return this.groups && (this.groups.length > 0)
        || this.model.groups && (this.model.groups.length > 0);
    },
    groupOptions: function() {
      if (!this.hasGroups) {
        return null;
      }

      let groups = this.groups || this.model.groups;
      return groups.map((group) => {
        return {
          id: group.id ? group.id : group.documentSelfLink,
          name: group.label ? group.label : group.name
        };
      });
    }
  },
  data: function() {
    return {
      showGroupForProvisioning: false,
      preferredGroupId: null,
      selectedGroup: null
    };
  },
  methods: {
    handleGroup: function(fnToCall, params) {

      if (this.hasGroups) {
        if (!this.showGroupForProvisioning) {

          this.preferredGroupId = localStorage.getItem('preferredGroupId');
          this.showGroupForProvisioning = true;
        } else {

          localStorage.setItem('preferredGroupId', this.preferredGroupId);
          this.showGroupForProvisioning = false;

          let groups = this.groups || this.model.groups;
          this.selectedGroup = groups.find((group) => {
            return group.id === this.preferredGroupId;
          });

          if (this.selectedGroup) {
            let group = this.selectedGroup.documentSelfLink
                              ? this.selectedGroup.documentSelfLink : this.selectedGroup.id;
            params.push(group);
          }

          fnToCall.apply(this, params);
        }
      } else {
        fnToCall.apply(this, params);
      }
    },
    toggleGroupsDisplay: function() {
      if (this.hasGroups) {
        this.showGroupForProvisioning = true;
        this.preferredGroupId = localStorage.getItem('preferredGroupId');
      } else {
        this.showGroupForProvisioning = false;
      }
    }
  }
};

export default ResourceGroupsMixin;
