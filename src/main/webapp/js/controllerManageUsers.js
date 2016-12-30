angular.module('BuildWhizApp')

.controller("ManageUsersCtrl", ['$log', '$http', 'AuthenticationService',
      function ($log, $http, AuthService) {

  var self = this;
  self.nameFilter = '';
  self.users = [];
  self.selectedUser = '';
  self.userSelected = false;

  self.findUsers = function() {
    var query = 'api/Person/{$or: [{first_name: {$regex: "' + self.nameFilter + '", $options: "i"}},' +
        '{last_name: {$regex: "' + self.nameFilter + '", $options: "i"}}]}';
    $log.log('GET ' + query);
    $http.get(query).then(
      function(res) {
        self.users = res.data;
      },
      function(res) {
        $log.log('ERROR GET ' + query);
      }
    )
  }

  self.enableDisable = function() {
    var query = 'baf/UserEnabledSet?person_id=' + self.selectedUser._id + '&enabled=' + self.selectedUser.enabled;
    $log.log('POST ' + query);
    $http.post(query).then(
      function(res) {
        $log.log('OK POST ' + query);
      },
      function(res) {
        $log.log('ERROR POST ' + query);
      }
    )
  }

  self.selectUser = function(user) {
    self.selectedUser = user;
    if (!user.hasOwnProperty('enabled')) {
      user.enabled = false;
    }
    self.userSelected = true;
  }

}]);