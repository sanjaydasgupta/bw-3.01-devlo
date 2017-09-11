angular.module('BuildWhizApp')

.controller("UserProfileCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.emailEnabled = AuthService.data.email_enabled;

  self.labels = [];
  self.selectedLabel = null;
  self.newLabelName = '';

  self.fetchLabels = function() {
    var query = 'baf/DocumentLabelsFetch';
    $log.log('GET ' + query);
    $http.get(query).then(
      function(res) {
        self.labels = res.data;
        $log.log('OK GET ' + query + ' (' + self.labels.length + ' labels)');
      },
      function(res) {
        alert('ERROR GET ' + query);
      }
    )
  }

  self.addNewLabelName = function() {
    var query = 'baf/DocumentLabelAdd?label_name=' + self.newLabelName;
    $log.log('ENTRY addNewLabelName() labelName =' + self.newLabelName);
    $http.post(query).then(
      function(res) {
        $log.log('OK POST ' + query);
        self.newLabelName = '';
        self.fetchLabels();
      },
      function(res) {
        alert('ERROR POST ' + query);
      }
    )
  }

  self.appendLabelName = function() {
    $log.log('Called appendLabelName() labelName =' + self.newLabelName);
  }

  self.deleteLabelName = function() {
    var query = 'baf/DocumentLabelDelete?label_name=' + self.selectedLabel.name;
    $log.log('ENTRY deleteLabelName() labelName =' + self.selectedLabel.name);
    $http.post(query).then(
      function(res) {
        $log.log('OK POST ' + query);
        self.selectedLabel = null;
        self.fetchLabels();
      },
      function(res) {
        alert('ERROR POST ' + query);
      }
    )
  }

  self.addNewLabelNameDisabled = function() {
    return self.newLabelName == '';
  }

  self.selectLabel = function(label) {
    self.selectedLabel = label;
    $log.log('Called selectLabel(' + label.name + ')');
  }

  self.labelRowColor = function(label) {
    return label == self.selectedLabel ? "yellow" : "white";
  }

  self.userPasswordSetDisabled = function() {
      return !self.oldPassword || !self.newPassword || !self.cnfPassword || (self.newPassword != self.cnfPassword);
  }

  self.userPasswordSet = function() {
    var sPersonID = AuthService.data._id;
    var postData = {old_password: self.oldPassword, new_password: self.newPassword, person_id: sPersonID};
    $log.log('Calling /baf/UserPasswordSet' + postData);
    $http.post('baf/UserPasswordSet', postData).then(
      function() {
        self.oldPassword = '';
          self.newPassword = '';
          self.cnfPassword = '';
        },
        function() {
          //self.oldPassword = '';
          //self.newPassword = '';
          //self.cnfPassword = '';
          alert('Failed to set password');
        }
      )
    }

  self.toggleEmail = function() {
    var query = 'baf/UserPropertySet?person_id=' + AuthService.data._id + '&property=email_enabled&value=' +
        self.emailEnabled;
    $log.log('POST ' + query);
    $http.post(query).then(
      function(res) {
        $log.log('OK POST ' + query);
      },
      function(res) {
        alert('ERROR email setting failed');
      }
    )
  }

  self.fetchLabels();

}]);