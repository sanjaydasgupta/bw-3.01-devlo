angular.module('BuildWhizApp')

.controller("BpmnCtrl", ['$log', '$http', '$routeParams', '$sce', '$filter', '$window',
    function ($log, $http, $routeParams, $sce, $filter, $window) {

  var self = this;

  self.panelHeading = '';

  self.processName = $routeParams.process;
  self.projectId = $routeParams.project_id;
  self.projectName = $routeParams.project_name;
  self.phaseId = $routeParams.phase_id;

  $log.log('Process-Name: ' + self.processName);
  $log.log('Project-Id: ' + self.projectId);
  $log.log('Project-Name: ' + self.projectName);
  $log.log('Phase-Id: ' + self.phaseId);
  
	//--------CREATE NULL OP FUNCTION-------//
	var nullOp = function() 
	{
		$http.get('etc/Environment').then(
		  function(resp){$log.log('OK Environment')},
		  function(resp){$log.log('ERROR Environment')}
		)
	}

  self.refresh = function() {
    var refreshLocation = $window.location + '&dt=' + escape(new Date());
    $log.log('Refresh location: ' + refreshLocation);
    $window.location = refreshLocation;
  }
/* Date picker option */
  self.dateOptions = {
    dateDisabled: false,
    formatYear: 'yy',
    maxDate: new Date(2018, 11, 31),
    minDate: new Date(2010, 0, 1),
    startingDay: 1
  };

  // https://github.com/bpmn-io/bower-bpmn-js
  // https://github.com/bpmn-io/bpmn-js-examples/tree/master/interaction
  // https://github.com/bpmn-io/bpmn-js-examples/tree/master/overlay

  var processTimers = [];
  var processVariables = [];
  var processActivities = [];
  var processCalls = [];
  var adminPersonId = null;
  /*SDG*/ var startDatetime = 0;

  var popupData = [];
  var tasks = [];
  var person=[];
  var clickableObjects = {};

        
//------------DEFINED VARRIABLES FOR ACTIVITYS----------//
  var activity_id =null;
//----------------------//
        
        
  var selectedElement = null;
  var bpmnViewer = new BpmnJS({container: '#canvas'});
  var overlayId = null;
  var hoverOverlayId = null;
  var overlays = bpmnViewer.get('overlays');
  var eventBus = bpmnViewer.get('eventBus');
        
  //--------------GET PHASE MANAGER LIST-----------//
   var resUrl = 'api/Person';
	 $http.get(resUrl).then(function(resp) {
       person = resp.data;	
       $log.log('person'+JSON.stringify(person));
      });
    

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

  var enableTimer = function(timerVal) {
    var resUrl = 'baf/TimerDurationFetch?phase_id='+self.phaseId+'&bpmn_name='+self.processName+'&timer_id='+timerVal;
	 $http.get(resUrl).then(function(resp) {
       var timer = resp.data;
		if(timer!=''){
          self.isVisible = 'timer';
          self.eltype = timerVal;		// get timer type
          self.getduration = timer;
        } else {
          self.isVisible = '';
        }
      })
  }

  /*** update time duraton   ***/
   self.setDuration_dt = function(clickedon, updated_time) {
    var dataset = 'baf/TimerDurationSet/?' + "method=1&phase_id=" + self.phaseId + "&bpmn_name=" + self.processName + "&timer_id=" + clickedon + "&duration=" + updated_time ;
    var objData='{\'method\':\'1\',\'phase_id\':\''+self.phaseId+'\',\'bpmn_name\':\''+self.processName+'\',\'timer_id\':\''+clickedon+'\',\'duration\':\''+updated_time+'\'}';
                 
       $log.log(dataset);
    $http({method: 'POST', url: dataset}).success(function (data){
      $log.log(data);
      $log.log('Success....');
	}).error(function (data, status, headers, config) {
        $log.log('Error....');
	});

  }

  self.setTaskDuration = function(task) {
    var q = 'baf/ActionDurationSet?activity_id=' + self.activity_id + '&duration=' + task.duration +
      '&action_name=' + task.name;
    $http.post(q).then(
      function(resp) {
        $log.log('OK POST: ' + q);
      },
      function(resp) {
        $log.log('ERROR POST: ' + q);
      }
    )
  }

//--------------ADD ACTIVITY-------------------//
   self.addAction = function(activity_id,action_name,type,assignee_id) {
    
       
        var dataset = 'baf/ActionAdd?' + "method=1&activity_id=" + activity_id + "&action_name=" + action_name + "&type=" + type + "&bpmn_name=" + self.processName + "&assignee_id=" +adminPersonId ;
    
       var objData='{\'method\':\'1\',\'activity_id\':\''+activity_id+'\',\'action_name\':\''+action_name+'\',\'type\':\''+type+'\',\'bpmn_name\':\''+self.processName+'\',\'assignee_id\':\''+adminPersonId+'\'}';
    
       
    /*var _url='baf/ActionAdd/';
       
    var objData={
        'method':'1',
        'activity_id':activity_id,
        'action_name':action_name,
        'type':type,
        'bpmn_name':bpmn_name
    };   
      */ 
    $log.log(objData);   
    $log.log(dataset);
    $http({method: 'POST', url: dataset,data:objData}).success(function (data){
      $log.log(data);
      $log.log('Success....');
	}).error(function (data, status, headers, config) {
        $log.log('Error..................');
        $log.log('-----------------------------------');
        $log.log(headers);
        $log.log('-----------------------------------');
        $log.log(status);
	});

  }
//---------------------------------------------//
   
//-----------ADD PHASE MANAGER-----------------//
    self.setPhasemanager_dt = function(person_id) 
    {
        
        var dataset = 'baf/PhaseAdministratorSet?' + "method=1&phase_id=" + self.phaseId + "&person_id=" + person_id+ "&project_id="+self.projectId;    
       var objData='{\'method\':\'1\',\'phase_id\':\''+self.phaseId+'\',\'person_id\':\''+self.person_id+'\',\'project_id\':\''+self.projectId+'\'}';

        $log.log(objData);   
        $log.log(dataset);
        $http({method: 'POST', url: dataset,data:objData}).success(function (data){
          $log.log(data);
          $log.log('Success....');
        }).error(function (data, status, headers, config) {
            $log.log('Error..................');
            $log.log('-----------------------------------');
            $log.log(headers);
            $log.log('-----------------------------------');
            $log.log(status);
        });

  }
   
//--------------------------------------------//
//------------------SET VALUE-------------------//
     self.setValueAdd = function(variable) 
    {
        var label=variable.label;
        var value=variable.value;
               
        var dataset = 'baf/VariableValueSet/?' + "method=1&phase_id=" + self.phaseId + "&label=" + label + "&bpmn_name=" + self.processName  + "&value=" +value ;
    
       var objData='{\'method\':\'1\',\'phase_id\':\''+self.phaseId+'\',\'label\':\''+label+'\',\'bpmn_name\':\''+self.processName+'\',\'value\':\''+value+'\'}';

        $log.log(objData);   
        $log.log(dataset);
        $http({method: 'POST', url: dataset,data:objData}).success(function (data){
          $log.log(data);
          $log.log('Success....');
        }).error(function (data, status, headers, config) {
            $log.log('Error..................');
            $log.log('-----------------------------------');
            $log.log(headers);
            $log.log('-----------------------------------');
            $log.log(status);
        });

  }
//----------------------------------------------//
//--------------SET ASSIGNEE-------------------//
     self.setAssignee = function(task) 
    {
        
        var dataset = 'baf/ActionContributorSet?' + "method=1&person_id=" + task.assignee + "&activity_id=" + self.activity_id+ "&action_name="+task.name+ "&project_id="+self.projectId;   
       var objData='{\'method\':\'1\',\'person_id\':\''+task.assignee._id+'\',\'activity_id\':\''+self.activity_id+'\',\'action_name\':\''+task.name+'\',\'project_id\':\''+self.projectId+'\'}';
         $log.log(dataset);
       
        $http({method: 'POST', url: dataset,data:objData}).success(function (data){
          $log.log(data);
          $log.log('Success....');
        }).error(function (data, status, headers, config) {
            $log.log('Error..................');
            $log.log('-----------------------------------');
            $log.log(headers);
            $log.log('-----------------------------------');
            $log.log(status);
        });

  }
//--------------------------------------------//
//--------------SET START DATE-------------------//
     
     self.setStartDate = function(dt) 
    {
        var timestamp = self.selectedDate.getTime(dt);
        //alert(new Date(timestamp).getTime());
        var dataset = 'baf/PhaseStartDateTimeSet/?' + "method=1&phase_id=" + self.phaseId + "&datetime=" + timestamp;
    
       var objData='{\'method\':\'1\',\'phase_id\':\''+self.phaseId+'\',\'datetime\':\''+timestamp+'\'}';

        $log.log(objData);   
        $log.log(dataset);
        $http({method: 'POST', url: dataset,data:objData}).success(function (data){
          $log.log(data);
          $log.log('Success....');
        }).error(function (data, status, headers, config) {
            $log.log('Error..................');
            $log.log('-----------------------------------');
            $log.log(headers);
            $log.log('-----------------------------------');
            $log.log(status);
        });

  }
//----------------------------------------------//

  var selectElement = function(element) {
      $log.log('Called selectElement(' + JSON.stringify(element) + ')');
    $log.log('Called selectElement()');
    if (overlayId != null) {
      overlays.remove(overlayId);
      /*SDG*/overlayId = null;
    }

	 if(element.type  == 'bpmn:CallActivity' && clickableObjects[element.id])
	 {
        nullOp();
		self.isVisible = 'activity';
		self.panelHeading = element.id;
        self.processVariables=processVariables;
		var data = $filter('filter')(popupData, {bpmn_id: element.id })[0];
         self.activity_id=data.id;
         
         self.person=person;
        //$log.log('person_drop:' + JSON.stringify(self.person));
         
		//$log.log('data:'+ JSON.stringify(data));
		self.tasks = data.tasks;
        $log.log('data:'+ JSON.stringify(self.tasks));
		$log.log('Activity Selected with:' + self.isVisible + ' Header:' +self.panelHeading);
        overlayId = overlays.add(element.id, {
           position: {top: -10, right: 17},
           html: overlayHtml()
        });
	 }
	else if(element.type  == 'bpmn:IntermediateCatchEvent'){
		nullOp();
		enableTimer(element.id);
		self.isVisible = 'timer';
		self.panelHeading = element.id;
		$log.log('Activity Selected with:' + self.isVisible + ' Header:' +self.panelHeading);
        overlayId = overlays.add(element.id, {
          position: {top: -10, right: 17},
          html: overlayHtml()
        });
	  }
	
	else if(element.type  == 'bpmn:Process')
    {
        nullOp();
        self.isVisible = 'process';
        self.adminPersonId=adminPersonId;
		self.selectedDate = new Date();
        /*SDG*/self.panelHeading = '';
        self.person=person;
        self.processVariables=processVariables;
        $log.log(JSON.stringify(processVariables));
        //$log.log('person_drop:' + JSON.stringify(self.person));
    } else {
      self.isVisible = '';
      self.panelHeading = '';
      nullOp();
    }

    //selectedElement = element;
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
    $log.log('Called doubleSelectElement()');
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
          processCalls = resp.data.calls;
          adminPersonId = resp.data.admin_person_id;
          startDatetime = resp.data.start_datetime;

		  processTimers.forEach(function(variable) {
            variable.width = 36;
		    variable.height = 36;
		    popupData.push(variable);
		    clickableObjects[variable.bpmn_id] = variable;
		  });

		  processActivities.forEach(function(variable) {
		    variable.width = 100;
		    variable.height = 80;
		    popupData.push(variable);
		    clickableObjects[variable.bpmn_id] = variable;
		  });

		  processCalls.forEach(function(variable) {
		    variable.width = 100;
		    variable.height = 80;
		    popupData.push(variable);
		  });
		  
		  $log.log('Popup data:'+ JSON.stringify(popupData));
		
          $log.log('OK importXML, timers: ' + processTimers.length + ', variables: ' + processVariables.length +
            ', activities: ' + processActivities.length + ', calls: ' + processCalls.length);
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