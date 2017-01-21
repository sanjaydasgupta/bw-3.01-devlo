angular.module('BuildWhizApp')

.controller("InformationCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.projectName = '430 Project';

}]);