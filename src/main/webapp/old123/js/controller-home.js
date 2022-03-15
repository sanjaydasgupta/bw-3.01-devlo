angular.module('BuildWhizApp')

.controller("HomeCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

//  self.taskList = [];
  self.projectList = [];
//  self.documentList = [];
  self.rfiStatus = '';
  self.tasksStatus = '';

  self.logout = function() {
    AuthService.logout();
  }

  self.refresh = function() {
    self.fullName = AuthService.data.first_name + ' ' + AuthService.data.last_name;
  }

  self.uiHidden = function() {
    $log.log('Called uiHidden()');
    var exists = AuthService.data.hasOwnProperty("ui_hidden");
    var hidden =  exists && AuthService.data.ui_hidden == true;
    $log.log('uiHidden() Exists -> ' + exists);
    $log.log('uiHidden() -> ' + hidden);
    return hidden;
  }

  self.refresh();

}]);