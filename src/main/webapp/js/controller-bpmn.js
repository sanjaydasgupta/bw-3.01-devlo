angular.module('BuildWhizApp')

.controller("BpmnCtrl", ['$log', '$http', '$routeParams', '$sce', '$filter', '$window',
    function ($log, $http, $routeParams, $sce, $filter, $window) {

  var self = this;

  self.processName = $routeParams.process;
  self.projectId = $routeParams.project_id;
  self.projectName = $routeParams.project_name;
  self.phaseId = $routeParams.phase_id;

  $log.log('Process-Name: ' + self.processName);
  $log.log('Project-Id: ' + self.projectId);
  $log.log('Project-Name: ' + self.projectName);
  $log.log('Phase-Id: ' + self.phaseId);

/* SDG */  self.refresh = function() {
/* SDG */    var refreshLocation = $window.location + '&dt=' + escape(new Date());
/* SDG */    $log.log('Refresh location: ' + refreshLocation);
/* SDG */    $window.location = refreshLocation;
/* SDG */  }

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

  var events = [/*'element.hover', 'element.out', */'element.click', 'element.dblclick', 'element.hover'
      /*'element.mousedown', 'element.mouseup'*/];

  var overlayHtml = function(width, height) {
    return '<div style="border-radius: 50%; width: 10px; height: 10px; background-color:green;"></div>';
  }

//  var anotationHtml = function(bgColor,start,end) {
//    return '<div style="background-color:'+bgColor+';white-space: nowrap;padding: 3px;border: 1px solid #333; border-radius: 5px; font-size: x-small;"><b>Start: </b>'+start+'<br /><b>End: </b>'+end+'</div>';
//  }
/* SDG */  var annotationHtml = function(bgColor,duration) {
/* SDG */    return '<div style="background-color:'+bgColor+'; white-space:nowrap; padding:3px; border:1px solid #333; border-radius:5px; font-size: x-small; width:50px;">'+duration+'</div>';
/* SDG */  }

  var hoverOverlayHtml = function(start,end) {
    return '<div style="width:100px;height:70px; background-color:#84FFFF; padding: 3px; border: 1px solid #333;font-size: x-small;"><b>Start: </b>'+start+'<br /><b>End: </b>'+end+'</div>';
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
      position: {top: -10, right: 17},
      html: overlayHtml()
    });
    selectedElement = element;
  }

  var hoverSelectElement = function(element) {
    $log.log('Called hoverSelectElement(' + JSON.stringify(element) + ')');
    if (hoverOverlayId != null) {
      overlays.remove(hoverOverlayId);
      hoverOverlayId = null;
    }
	
    if (element.type != 'bpmn:Process') {
		var data = $filter('filter')(popupData, {bpmn_id: element.id })[0];
		if (data !== undefined) {
			 hoverOverlayId = overlays.add(element.id, {
//				position: {top: 25,
//					left: 50},
/* SDG */                position: {
/* SDG */                  top: (element.height - 70) / 2,
/* SDG */                  left: (element.width - 100) / 2
/* SDG */                },
				html: hoverOverlayHtml(data.start,data.end)
			});
		}
	}
  }

  var doubleSelectElement = function(element) {
    $log.log('Called doubleSelectElement()');
  }

  var eventHandler = function(event) {
    // e.element = the model element
    // e.gfx = the graphical element
    var element = event.element;
    $log.log(event.type);
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
    extraEventHandlingCode(element);
  }

  events.forEach(function(event) {
    eventBus.on(event, eventHandler);
  });
  
//  var annotateGenerate = function(variable,type){
/* SDG */  var annotateGenerate = function(variable){
	var bgcolor='transparent';
	//$log.log('Type of variable:' + type);
	switch(variable.status) {
	  case "defined":
		bgcolor='yellow';
		break;
	  case "waiting":
		bgcolor='#F88017'; // Dark orange
		break;
	  case "started":
		bgcolor='#6CC417'; // Alien green
		break;
	  case "running":
		bgcolor='#6CC417'; // Alien green
		break;
	  case "ended":
	    bgcolor='#98AFC7'; // Blue gray
	    break;
	  default:
	    bgcolor='white';
    }

/* SDG */    overlays.add(variable.bpmn_id, {
/* SDG */      position: {
/* SDG */        top: -30,
/* SDG */        left: (variable.width - 50) / 2
/* SDG */      },
/* SDG */      html: annotationHtml(bgcolor,variable.duration)
/* SDG */    });

//	  if(type == 'typeTimers'){
//		  overlays.add(variable.bpmn_id, {
//			  position: {
//				top: -40,
//				left: -20
//			  },
//			  html: annotationHtml(bgcolor,variable.start,variable.end)
//			});
//	  }else {
//		  overlays.add(variable.bpmn_id, {
//			  position: {
//				top: -40,
//				left: 9
//			  },
//			  html: annotationHtml(bgcolor,variable.start,variable.end)
//			});
//	  }
  }

  var annotateBpmn = function() {
/* SDG */    popupData.forEach(function(e){
/* SDG */	  annotateGenerate(e);
/* SDG */    })
//    processTimers.forEach(function(timer) {
//      $log.log(JSON.stringify(timer));
//	  var type = 'typeTimers';
//	  annotateGenerate(timer,type);
//    });
//
//    processVariables.forEach(function(variable) {
//      $log.log(JSON.stringify(variable));
//	  var type = 'typeVariables';
//	  annotateGenerate(variable,type);
//    });
//
//    processActivities.forEach(function(activity) {
//      $log.log(JSON.stringify(activity));
//	  var type = 'typeActivities';
//	  annotateGenerate(activity,type);
//    });
//
//    callActivities.forEach(function(activity) {
//      $log.log(JSON.stringify(activity));
//	  var type = 'typeActivities';
//	  annotateGenerate(activity,type);
//    });
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
/* SDG */            variable.width = 36;
/* SDG */		    variable.height = 36;
		    popupData.push(variable);
		  });
//		  processVariables.forEach(function(variable) {
//			 popupData.push(variable);
//		  });
		  processActivities.forEach(function(variable) {
/* SDG */		    variable.width = 100;
/* SDG */		    variable.height = 80;
		    popupData.push(variable);
		  });
		  callActivities.forEach(function(variable) {
/* SDG */		    variable.width = 100;
/* SDG */		    variable.height = 80;
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