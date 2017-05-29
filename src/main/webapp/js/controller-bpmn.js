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
  var callActivities = [];

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

  var AnotationHtml = function(bgColor,start,end) {
    return '<div style="background-color:'+bgColor+';white-space: nowrap;padding: 3px;border: 1px solid #333; border-radius: 5px; font-size: x-small;"><b>Start: </b>'+start+'<br /><b>End: </b>'+end+'</div>';
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

	  var bgcolor='transparent';

	  switch(timer.status) {
		case "defined":
			bgcolor='yellow';
			break;
		case "started":
			bgcolor='#AED581';
			break;
		case "ended":
			bgcolor='#9E9E9E';
			break;
	}

	  overlays.add(timer.bpmn_id, {
		  position: {
			top: -40,
			left: -20
		  },
		  html: AnotationHtml(bgcolor,timer.start,timer.end)
		});
    });
    processVariables.forEach(function(variable) {
      $log.log(JSON.stringify(variable));
    });

    processActivities.forEach(function(activity) {
      $log.log(JSON.stringify(activity));
	  var bgcolor='transparent';

	switch(activity.status) {
		case "defined":
			bgcolor='yellow';
			break;
		case "started":
			bgcolor='#AED581';
			break;
		case "ended":
			bgcolor='#9E9E9E';
			break;
	}

	  overlays.add(activity.bpmn_id, {
		  position: {
			top: -40,
			left: 9
		  },
		  html: AnotationHtml(bgcolor,activity.start,activity.end)
		});
    });

    callActivities.forEach(function(activity) {
      $log.log(JSON.stringify(activity));
	  var bgcolor='transparent';

	switch(activity.status) {
		case "defined":
			bgcolor='yellow';
			break;
		case "started":
			bgcolor='#AED581';
			break;
		case "ended":
			bgcolor='#9E9E9E';
			break;
	}

	  overlays.add(activity.bpmn_id, {
		  position: {
			top: -40,
			left: 9
		  },
		  html: AnotationHtml(bgcolor,activity.start,activity.end)
		});
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
          callActivities = resp.data.calls;

          $log.log('OK importXML, timers: ' + processTimers.length + ', variables: ' + processVariables.length +
            ', activities: ' + processActivities.length + ', calls: ' + callActivities.length);
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