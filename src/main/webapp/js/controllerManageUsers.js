angular.module('BuildWhizApp')

.controller("ManageUsersCtrl", ['$log', '$http', 'AuthenticationService',
      function ($log, $http, AuthenticationService) {

  var self = this;
  self.nameFilter = "";
  self.users = [];

  self.findUsers = function() {
    var query = 'api/Person/{$or: [{first_name: {$regex: "' + self.nameFilter + '", $options: "i"}},' +
        '{last_name: {$regex: "' + self.nameFilter + '", $options: "i"}}]}';
    $log.log('GET ' + query);
    $http.get(query).then(
      function(res) {
        self.users = res.data;
      },
      function(res) {

      }
    )
  }

  self.selectUser = function(user) {
    $log.log('selectUser(' + user.first_name + ')')
  }

}]);