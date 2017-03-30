angular.module('BuildWhizApp')

.controller("BpmnCtrl", ['$log', '$http', 'AuthenticationService', '$window', '$routeParams', '$sce',
    function ($log, $http, AuthService, $window, $routeParams, $sce) {

  var self = this;

  self.processKey = $routeParams.hasOwnProperty("process") ? $routeParams.process : null;
  self.projectId = $routeParams.hasOwnProperty("project_id") ? $routeParams.project_id : null;
  self.phaseId = $routeParams.hasOwnProperty("phase_id") ? $routeParams.phase_id : null;

  $log.log('Process: ' + self.processKey);
  $log.log('projectId: ' + self.projectId);
  $log.log('PhaseId: ' + self.phaseId);

}]);