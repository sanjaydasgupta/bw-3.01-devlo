angular.module('BuildWhizApp')

.controller('MainController', ['AuthenticationService', '$log', function (AuthService, $log) {
  var self = this;

  self.isNavCollapsed = true;
  self.loggedIn = false;
  self.tryAgain = false;
  self.fullName = '';

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
}]);