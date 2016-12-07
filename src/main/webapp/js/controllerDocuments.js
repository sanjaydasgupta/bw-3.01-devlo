angular.module('BuildWhizApp')

.controller("DocumentsCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {
  $log.log('ENTRY DocumentsCtrl');
  var self = this;

  self.documentList = [];

  $log.log('EXIT DocumentsCtrl');
}]);