angular.module('BuildWhizApp')

.controller('MainController', ['AuthenticationService', '$http', '$log', function (AuthService, $http, $log) {
  var self = this;

  self.isNavCollapsed = true;
  self.loggedIn = false;
  self.tryAgain = false;
  self.username = '';
  self.instance = '';

  $log.log('HTTP GET etc/Environment');
  $http.get('etc/Environment').then(
    function(response) {
      var env = response.data;
      $log.log('Environment: ' + JSON.stringify(env));
      self.username = env.email;
      self.instance = env.instance;
      if (env.hasOwnProperty('user')) {
        AuthService.setupLoginData(env.user, self);
      }
      $log.log('self.loggedIn: ' + self.loggedIn);
    }
  );

  self.headerName = function() {
    return self.loggedIn ? (self.data.first_name + ' ' + self.data.last_name) : '';
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