angular.module('BuildWhizApp')

.controller("BpmnCtrl", ['$log', '$http', '$routeParams', '$sce',
    function ($log, $http, $routeParams) {

  var self = this;

  self.processName = $routeParams.process;
  self.projectId = $routeParams.project_id;
  self.projectName = $routeParams.project_name;
  self.phaseId = $routeParams.phase_id;

  $log.log('Process-Name: ' + self.processName);
  $log.log('Project-Id: ' + self.projectId);
  $log.log('Project-Name: ' + self.projectName);
  $log.log('Phase-Id: ' + self.phaseId);

  // https://github.com/bpmn-io/bower-bpmn-js
  // https://github.com/bpmn-io/bpmn-js-examples/tree/master/interaction
  // https://github.com/bpmn-io/bpmn-js-examples/tree/master/overlay

  var processTimers = [];
  var processVariables = [];
  var processActivities = [];
  var selectedElement = null;
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

  var extraEventHandlingCode = function(element) {
    $log.log('Called extraEventHandlingCode()')
    // All additions here
  }

  var selectElement = function(element) {
    $log.log('Called selectElement()')
    if (overlayId != null) {
      overlays.remove(overlayId);
    }
    overlayId = overlays.add(element.id, {
      position: {top: -5, left: -5},
      html: overlayHtml(element.width, element.height)
    });
    selectedElement = element;
  }

  var eventHandler = function(event) {
    // e.element = the model element
    // e.gfx = the graphical element
    var element = event.element;
    $log.log(JSON.stringify(element));
    selectElement(element);
    extraEventHandlingCode(element);
  }

  events.forEach(function(event) {
    eventBus.on(event, eventHandler);
  });

  var annotateBpmn = function() {
    processTimers.forEach(function(timer) {
      $log.log(JSON.stringify(timer));
    });
    processVariables.forEach(function(variable) {
      $log.log(JSON.stringify(variable));
    });
    processActivities.forEach(function(activity) {
      $log.log(JSON.stringify(activity));
    });
  }

  var q = 'baf/PhaseBpmnXml?bpmn_name=' + self.processName + '&phase_id=' + self.phaseId;
  $http.get(q).then(
    function(resp) {
      bpmnViewer.importXML(resp.data.xml, function(err) {
        if (err) {
          // import failed :-(
          $log.log('FAIL importXML: ' + err);
        } else {
          // we did well!
          var canvas = bpmnViewer.get('canvas');
          canvas.zoom('fit-viewport');
          processTimers = resp.data.timers;
          processVariables = resp.data.variables;
          processActivities = resp.data.activities;
          $log.log('OK importXML, timers: ' + processTimers.length + ', variables: ' + processVariables.length +
            ', activities: ' + processActivities.length);
          annotateBpmn();
        }
      })
      $log.log('OK GET ' + q);
    },
    function() {
      $log.log('ERROR GET ' + q);
    }
  );

}]);