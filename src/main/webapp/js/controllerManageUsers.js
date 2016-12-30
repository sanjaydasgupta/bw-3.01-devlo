angular.module('BuildWhizApp')

.controller("ManageUsersCtrl", ['$log', '$http', 'AuthenticationService',
      function ($log, $http, AuthService) {

  var self = this;
  self.nameFilter = '';
  self.users = [];
  self.selectedUser = null;
  self.userSelected = false;
  self.allRoles = [];
  self.userRoles = [];

  $http.get('api/Role').then(
    function(res) {
      self.allRoles = res.data;
      $log.log('Got ' + self.allRoles.length + ' roles');
    },
    function(res) {
      $log.log('ERROR failed to get roles');
    }
  )

  self.findUsers = function() {
    var query = 'api/Person/{$or: [{first_name: {$regex: "' + self.nameFilter + '", $options: "i"}},' +
        '{last_name: {$regex: "' + self.nameFilter + '", $options: "i"}}]}';
    $log.log('GET ' + query);
    $http.get(query).then(
      function(res) {
        self.users = res.data;
        self.userSelected = false;
        self.selectedUser = null;
      },
      function(res) {
        $log.log('ERROR GET ' + query);
      }
    )
  }

  self.enableDisable = function() {
    var query = 'baf/UserPropertySet?person_id=' + self.selectedUser._id + '&property=enabled&value=' +
        self.selectedUser.enabled;
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
    if (!user.hasOwnProperty('enabled')) {
      user.enabled = false;
    }
    self.userRoles = [];
    self.allRoles.forEach(function(role) {
      var roleKey = role.category + ':' + role.name;
      var newRole = {key: roleKey, ok: false}
      user.roles.forEach(function(ur) {
        if (ur == roleKey) {
          newRole.ok = true;
        }
      })
      self.userRoles.push(newRole);
    })
    self.selectedUser = user;
    self.userSelected = true;
  }

  self.roleToggle = function(role) {
    var query = 'baf/UserPropertySet?person_id=' + self.selectedUser._id + '&property=role:' + role.key +
        '&value=' + role.ok;
    $log.log('POST ' + query);
    $http.post(query).then(
      function(res) {
        $log.log('OK POST ' + query);
        if (role.ok) {
          self.selectedUser.roles.push(role.key);
        } else {
          self.selectedUser.roles = self.selectedUser.roles.filter(function(r) {return r != role.key})
        }
      },
      function(res) {
        $log.log('ERROR POST ' + query);
      }
    )
  }

}]);