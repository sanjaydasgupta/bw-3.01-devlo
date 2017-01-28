angular.module('BuildWhizApp')

.controller("RoleCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.categories = [];
  self.selectedCategory = null;
  self.roles = [];
  self.selectedRole = null;

  $http.get('api/Role').then(
    function(resp) {
      self.roles = resp.data;
      $log.log('Got ' + self.roles.length + ' roles');
    }
  )

  self.selectRole = function(role) {
    $log.log('Called selectRole(' + role.category + '/' + role.name + ')');
    q = 'baf/DocCategoryRoleFetch?role_id=' + role._id;
    $log.log('Calling GET ' + q)
    $http.get(q).then(
      function(resp) {
        self.categories = resp.data;
        self.selectedRole = role;
        $log.log('OK ' + q);
      },
      function(resp) {
        $log.log('ERROR ' + q);
      }
    )
  }

  self.roleColor = function(role) {
    return role == self.selectedRole ? 'yellow' : 'white';
  }

  self.togglePermission = function(cat) {
    $log.log('Called togglePermission(' + cat.category + ')');
    q = 'baf/DocCategoryRoleSet?role_id=' + self.selectedRole._id + '&category_id=' + cat._id + '&permitted=' + cat.permitted;
    $log.log('Calling POST ' + q)
    $http.post(q).then(
      function(resp) {
        $log.log('OK ' + q);
      },
      function(resp) {
        $log.log('ERROR ' + q);
      }
    )
  }

}]);