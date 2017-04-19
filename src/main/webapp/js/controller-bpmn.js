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

  // https://github.com/bpmn-io/bower-bpmn-js
  // https://github.com/bpmn-io/bpmn-js-examples/tree/master/interaction

  var bpmnViewer = new BpmnJS({container: '#canvas'});
  var overlayId = null;
  var overlays = bpmnViewer.get('overlays');
  var eventBus = bpmnViewer.get('eventBus');
  var events = [/*'element.hover', 'element.out', */'element.click', 'element.dblclick',
      /*'element.mousedown', 'element.mouseup'*/];

  var overlayHtml = function(width, height) {
    return '<div style="background-color:blue;opacity:0.2;width:' + (parseInt(width) + 10) + 'px;height:' +
        (parseInt(height) + 10) + 'px;">&nbsp;</div>';
  }

  events.forEach(function(event) {
    eventBus.on(event, function(e) {
      // e.element = the model element
      // e.gfx = the graphical element
      if (overlayId != null) {
        overlays.remove(overlayId);
      }
      overlayId = overlays.add(e.element.id, {
        position: {top: -5, left: -5},
        html: overlayHtml(e.element.width, e.element.height)
      });
      $log.log(JSON.stringify(e.element));
    });
  });

  var q = 'baf/PhaseBpmnXml?bpmn_name=' + self.processName;
  $http.get(q).then(
    function(resp) {
      bpmnViewer.importXML(resp.data, function(err) {
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