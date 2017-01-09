angular.module('BuildWhizApp')

.controller('MainController', ['AuthenticationService', '$http', '$log', function (AuthService, $http, $log) {
  var self = this;

  self.isNavCollapsed = true;
  self.loggedIn = false;
  self.tryAgain = false;
  self.username = '';

  $log.log('HTTP GET etc/Environment');
  $http.get('etc/Environment').then(
    function(response) {
      var env = response.data;
      $log.log('etc/Environment -> ' + JSON.stringify(env));
      self.username = env.email;
      if (env.timezone_raw_offset == 19800000) {
        // to facilitate testing on local machine
      }
    }
  );

  self.headerName = function() {
    return self.loggedIn ? (self.data.first_name + ' ' + self.data.last_name) : 'BuildWhiz';
  }

  self.login = function() {
    AuthService.login(self.username, self.password, self);
  }

  self.logout = function() {
    AuthService.logout(self)
    //self.loggedIn = AuthService.loggedIn
    $log.log('MainController: loggedIn=' + AuthService.loggedIn);
    self.clickCommon();
  }

  self.clickCommon = function() {
    self.isNavCollapsed = !self.isNavCollapsed;
  }

  self.isAdmin = function() {
    return self.data.roles.join(',').indexOf('BW-Admin') != -1;
  }
}]);