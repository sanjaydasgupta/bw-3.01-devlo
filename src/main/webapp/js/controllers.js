angular.module('BuildWhizApp')

.controller('MainController', ['AuthenticationService', '$log', function (AuthService, $log) {
  var self = this;

  self.isNavCollapsed = true;

  self.loggedIn = false;
  self.tryAgain = false;

  self.login = function() {
    AuthService.login(self.username, self.password, self);
    $log.log('MainController.loggedIn: ' + self.loggedIn);
    if (self.loggedIn) {
      self.password = '';
    } else {
      // Error message
    }
  }

  self.logout = function() {
    AuthService.logout(self)
    //self.loggedIn = AuthService.loggedIn
    $log.log('MainController.loggedIn: ' + self.loggedIn);
    self.clickCommon();
  }

  self.clickCommon = function() {
    self.isNavCollapsed = !self.isNavCollapsed;
  }
}])