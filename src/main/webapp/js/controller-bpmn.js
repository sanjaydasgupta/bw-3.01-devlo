angular.module('BuildWhizApp')

.controller("BpmnCtrl", ['$log', '$http', '$routeParams', '$sce', '$filter', '$window',
    function ($log, $http, $routeParams, $sce, $filter, $window) {

  var self = this;

  self.processName = $routeParams.process;
  self.projectId = $routeParams.project_id;
  self.projectName = $routeParams.project_name;
  self.phaseId = $routeParams.phase_id;

  self.visibleElmt = '????'
  self.heading = '????';

  $log.log('Process-Name: ' + self.processName);
  $log.log('Project-Id: ' + self.projectId);
  $log.log('Project-Name: ' + self.projectName);
  $log.log('Phase-Id: ' + self.phaseId);

  self.refresh = function() {
    var refreshLocation = $window.location + '&dt=' + escape(new Date());
    $window.location = refreshLocation;
  }

  // https://github.com/bpmn-io/bower-bpmn-js
  // https://github.com/bpmn-io/bpmn-js-examples/tree/master/interaction
  // https://github.com/bpmn-io/bpmn-js-examples/tree/master/overlay

  var processTimers = [];
  var processVariables = [];
  var processActivities = [];
  var callActivities = [];

  var popupData = [];

  var selectedElement = null;
  var bpmnViewer = new BpmnJS({container: '#canvas'});
  var overlayId = null;
  var hoverOverlayId = null;
  var overlays = bpmnViewer.get('overlays');
  var eventBus = bpmnViewer.get('eventBus');

  var events = [/*'element.out', */'element.click', 'element.dblclick', 'element.hover'
      /*'element.mousedown', 'element.mouseup'*/];

  var overlayHtml = function(width, height) {
    return '<div style="border-radius: 50%; width: 10px; height: 10px; background-color:green;"></div>';
  }

  var annotationHtml = function(bgColor,duration) {
    return '<div style="background-color:'+bgColor+'; white-space:nowrap; padding:3px; border:1px solid #333; border-radius:5px; font-size: x-small; width:50px;">'+duration+'</div>';
  }

  var hoverOverlayHtml = function(start,end) {
    return '<div style="width:100px;height:70px; background-color:#84FFFF; padding: 3px; border: 1px solid #333;font-size: x-small;"><b>Start: </b>'+start+'<br /><b>End: </b>'+end+'</div>';
  }

  var extraEventHandlingCode = function(element) {
    //$log.log('Called extraEventHandlingCode()')
    // All additions here
  }

//  var enableTimer = function(timerVal) {
//    var resUrl = 'baf/TimerDurationFetch?phase_id='+self.phaseId+'&bpmn_name='+self.processName+'&timer_id='+timerVal;
//	 $http.get(resUrl).then(function(resp) {
//       var timer = resp.data;
//		if(timer!=''){
//          self.visibleElmt = 'timer';
//          self.eltype = timerVal;		// get timer type
//          self.getduration = timer;
//        } else {
//          self.visibleElmt = '';
//        }
//      })
//  }

  /*** update time duraton   ***/
//   self.setDuration_dt = function(clickedon, updated_time) {
//    var dataset = 'baf/TimerDurationSet/?' + "method=1&phase_id=" + self.phaseId + "&bpmn_name=" + self.processName + "&timer_id=" + clickedon + "&duration=" + updated_time ;
//
//    $http({method: 'POST', url: dataset}).success(function (data){
//      //$log.log(data);
//	}).error(function (data, status, headers, config) {
//	});
//
//  }

  var selectElement = function(element) {
    //$log.log('Called selectElement()');
//    if (overlayId != null) {
//      overlays.remove(overlayId);
//    }
//    overlayId = overlays.add(element.id, {
//      position: {top: -10, right: 17},
//      html: overlayHtml()
//    });
	 if (element.type  == 'bpmn:CallActivity') {
		self.visibleElmt = 'activity';
		self.heading = element.id;
		$log.log('Selected ACTIVITY: ' + self.visibleElmt + ' Header:' +self.heading);
	 } else if(element.type  == 'bpmn:IntermediateCatchEvent'){
		//enableTimer(element.id);
		self.visibleElmt = 'timer';
		self.heading = element.id;
		$log.log('Selected TIMER: ' + self.visibleElmt + ' Header:' +self.heading);
	  }

    selectedElement = element;
  }

  var hoverSelectElement = function(element) {
    //$log.log('Called hoverSelectElement(' + JSON.stringify(element) + ')');
    if (hoverOverlayId != null) {
      overlays.remove(hoverOverlayId);
      hoverOverlayId = null;
    }

    if (element.type != 'bpmn:Process') {
		var data = $filter('filter')(popupData, {bpmn_id: element.id })[0];
		if (data !== undefined) {
			 hoverOverlayId = overlays.add(element.id, {
                position: {
                  top: 10,
                  left: 25
                },
				html: hoverOverlayHtml(data.start,data.end)
			});
		}
	}
  }

  var doubleSelectElement = function(element) {
    //$log.log('Called doubleSelectElement()');
  }

  var eventHandler = function(event) {
    // e.element = the model element
    // e.gfx = the graphical element
    var element = event.element;
    //$log.log(event.type);
	switch(event.type) {
		case "element.hover":
			hoverSelectElement(element);
			break;
		case "element.click":
			selectElement(element);
			break;
		case "element.dblclick":
			doubleSelectElement(element);
			break;
	}
    //extraEventHandlingCode(element);
  }

  events.forEach(function(event) {
    eventBus.on(event, eventHandler);
  });

  var annotateGenerate = function(variable){
	var bgcolor='transparent';
	switch(variable.status) {
	  case "defined":
		bgcolor='yellow';
		break;
	  case "waiting":
		bgcolor='Red';
		break;
	  case 'waiting2':
		bgcolor = 'Pink';
		break;
	  case "started":
		bgcolor='Lime';
		break;
	  case "running":
		bgcolor='Lime';
		break;
	  case "ended":
	    bgcolor='Silver';
	    break;
	  default:
	    bgcolor='white';
    }

    overlays.add(variable.bpmn_id, {
      position: {
        top: -30,
        left: (variable.width - 50) / 2
      },
      html: annotationHtml(bgcolor,variable.duration)
    });

  }

  var annotateBpmn = function() {
    popupData.forEach(function(e){
	  annotateGenerate(e);
    })

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

		  processTimers.forEach(function(variable) {
            variable.width = 36;
		    variable.height = 36;
		    popupData.push(variable);
		  });

		  processActivities.forEach(function(variable) {
		    variable.width = 100;
		    variable.height = 80;
		    popupData.push(variable);
		  });
		  callActivities.forEach(function(variable) {
		    variable.width = 100;
		    variable.height = 80;
		    popupData.push(variable);
		  });
		  
		  $log.log('Popup data:'+ JSON.stringify(popupData));
		
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