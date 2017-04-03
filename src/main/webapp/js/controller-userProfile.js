angular.module('BuildWhizApp')

.controller("UserProfileCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.emailEnabled = false;
  if (AuthService.data.email_enabled) {
    self.emailEnabled = true;
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

}]);