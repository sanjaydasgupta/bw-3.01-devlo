angular.module('BuildWhizApp')

.controller("BpmnCtrl", ['$log', '$http', 'AuthenticationService', '$window', '$routeParams', '$sce',
    function ($log, $http, AuthService, $window, $routeParams, $sce) {

  var self = this;

  self.processName = $routeParams.hasOwnProperty("process") ? $routeParams.process : null;
  self.projectId = $routeParams.hasOwnProperty("project_id") ? $routeParams.project_id : null;
  self.projectName = $routeParams.hasOwnProperty("project_name") ? $routeParams.project_name : null;
  self.phaseId = $routeParams.hasOwnProperty("phase_id") ? $routeParams.phase_id : null;

  $log.log('Process: ' + self.processName);
  $log.log('projectId: ' + self.projectId);
  $log.log('PhaseId: ' + self.phaseId);

  self.bpmnViewer = new BpmnJS({container: '#canvas'});

  var q = 'baf/PhaseBpmnXml?bpmn_name=Phase-' + self.processName;
  $http.get(q).then(
    function(resp) {
      self.bpmnViewer.importXML(resp.data, function(err) {
        if (err) {
          // import failed :-(
          $log.log('FAIL importXML');
        } else {
          // we did well!
          var canvas = viewer.get('canvas');
          canvas.zoom('fit-viewport');
        }
      })
      $log.log('OK GET ' + q);
    },
    function() {
      $log.log('ERROR GET ' + q);
    }
  );

}]);