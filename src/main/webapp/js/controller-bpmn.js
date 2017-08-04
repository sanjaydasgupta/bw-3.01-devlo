angular.module('BuildWhizApp')
    .controller("BpmnCtrl", ['$log', '$http', '$routeParams', '$sce', '$filter', '$window',
        function ($log, $http, $routeParams, $sce, $filter, $window) {

            /* 
            https://github.com/bpmn-io/bower-bpmn-js
            https://github.com/bpmn-io/bpmn-js-examples/tree/master/interaction
            https://github.com/bpmn-io/bpmn-js-examples/tree/master/overlay
            */

            //--------START COMMON VARIABLES -------//
            var self = this;

            self.panelHeadingType = '';
            self.panelHeadingName = '';
            self.selectedItemType = '';

            self.processName = $routeParams.process;
            self.projectId = $routeParams.project_id;
            self.projectName = $routeParams.project_name;
            self.phaseId = $routeParams.phase_id;
            /*SDG*/ self.processStartDatetime = new Date();

            var events = [
                            'element.click',
                            'element.dblclick',
                            'element.hover',
                            /*'element.out', */  
                            /*'element.mousedown',*/
                            /*'element.mouseup'*/
                        ];

            var bpmnViewer = new BpmnJS({ container: '#canvas' });
            var overlays = bpmnViewer.get('overlays');
            var eventBus = bpmnViewer.get('eventBus');

            //--------END COMMON VARIABLES-------//


            $log.log('Process-Name: ' + self.processName);
            $log.log('Project-Id: ' + self.projectId);
            $log.log('Project-Name: ' + self.projectName);
            $log.log('Phase-Id: ' + self.phaseId);
            

            //Date picker options
            self.dateOptions = {
                dateDisabled: false,
                formatYear: 'yy',
                maxDate: new Date(2018, 11, 31),
                minDate: new Date(2010, 0, 1),
                startingDay: 1
            };

	        var nullOp = function() {
		        $http.get('etc/Environment').then(
		          function(resp){$log.log('OK Environment')},
		          function(resp){$log.log('ERROR Environment')}
		        )
	        }

            //Refresh page
            self.refresh = function() {
                var refreshLocation = $window.location + '&dt=' + escape(new Date());
                //$log.log('Refresh location: ' + refreshLocation);
                $window.location = refreshLocation;
            }

            //Get status color
            self.statusColor = function (status) {
                var resultColor = 'transparent';
                switch (status) {
                    case "defined":
                        resultColor = 'yellow';
                        break;
                    case "waiting":
                        resultColor = 'Red';
                        break;
                    case 'waiting2':
                        resultColor = 'Pink';
                        break;
                    case "started":
                        resultColor = 'Lime';
                        break;
                    case "running":
                        resultColor = 'Lime';
                        break;
                    case "ended":
                        resultColor = 'Silver';
                        break;
                    default:
                        resultColor = 'white';
                        break;
                }

                return resultColor;
            }

            //Get person list
            var person = [];
            var requestUrl = 'api/Person';
            $http.get(requestUrl).then(function (response) {
                person = response.data;
                //$log.log('person' + JSON.stringify(person));
            });


            var processTimers = [];
            var processVariables = [];
            var processActivities = [];
            var processCalls = [];
            var adminPersonId = null;
            //SDG var startDatetime = 0;
            var bpmnElements = [];
            var clickableObjects = {};

            //Generte div to display duration on initialize
            var annotationOverlayHtml = function (bgColor, duration) {
                return '<div style="background-color:' + bgColor + '; white-space:nowrap; padding:3px; border:1px solid #333; border-radius:5px; font-size: x-small; width:50px;">' + duration + '</div>';
            }

            //Initialize bpmn
            var initUrl = 'baf/PhaseBpmnXml?bpmn_name=' + self.processName + '&phase_id=' + self.phaseId;
            $http.get(initUrl).then(
              function (response) {
                  bpmnViewer.importXML(response.data.xml, function (err) {
                      if (err) {
                          // import failed :-(
                          $log.log('FAIL importXML: ' + err);
                      } else {
                          // we did well!
                          var canvas = bpmnViewer.get('canvas');
                          canvas.zoom('fit-viewport');

                          processTimers = response.data.timers;
                          processVariables = response.data.variables;
                          processActivities = response.data.activities;
                          processCalls = response.data.calls;
                          adminPersonId = response.data.admin_person_id;
                          self.processStartDatetime = new Date(response.data.start_datetime);

                          //timers loop
                          processTimers.forEach(function (processTimer) {
                              processTimer.width = 36;
                              processTimer.height = 36;
                              bpmnElements.push(processTimer);
                              clickableObjects[processTimer.bpmn_id] = processTimer;
                          });

                          //activities loop
                          processActivities.forEach(function (processActivity) {
                              processActivity.width = 100;
                              processActivity.height = 80;
                              bpmnElements.push(processActivity);
                              clickableObjects[processActivity.bpmn_id] = processActivity;
                          });

                          //calls loop
                          processCalls.forEach(function (processCall) {
                              processCall.width = 100;
                              processCall.height = 80;
                              bpmnElements.push(processCall);
                              clickableObjects[processCall.bpmn_id] = processCall;
                          });

                          //$log.log('Popup data:' + JSON.stringify(popupData));

                          $log.log('OK importXML.\n'+
                                    'timers: ' + processTimers.length + '\n' +
                                    'variables: ' + processVariables.length + '\n' +
                                    'activities: ' + processActivities.length + '\n' +
                                    'calls: ' + processCalls.length);

                          annotateBPMN();
                      }
                  })
                  $log.log('OK GET ' + initUrl);
              },
              function () {
                  $log.log('ERROR GET ' + initUrl);
              }
            );

            var annotateBPMN = function () {
                bpmnElements.forEach(function (bpmnElement) {
                    var bgcolor = self.statusColor(bpmnElement.status);

                    overlays.add(bpmnElement.bpmn_id, {
                        position: {
                            top: -30,
                            left: (bpmnElement.width - 50) / 2
                        },
                        html: annotationOverlayHtml(bgcolor, bpmnElement.duration)
                    });
                });
            }

            //--------------START EVENT BINDING-----------//            
            var hoverOverlayId = null;
            //Generte div to display information on hover
            var hoverOverlayHtml = function (start, end) {
                return '<div style="width:100px;height:70px; background-color:#84FFFF; padding: 3px; border: 1px solid #333;font-size: x-small;"><b>Start: </b>' + start + '<br /><b>End: </b>' + end + '</div>';
            }
            //hover event
            var hoverElement = function (element) {
                if (hoverOverlayId != null) {
                    overlays.remove(hoverOverlayId);
                    hoverOverlayId = null;
                }

                if (element.type != 'bpmn:Process') {
                    var data = $filter('filter')(bpmnElements, { bpmn_id: element.id })[0];
                    if (data !== undefined) {
                        hoverOverlayId = overlays.add(element.id, {
                            position: {
                                top: 10,
                                left: 25
                            },
                            html: hoverOverlayHtml(data.start, data.end)
                        });
                    }
                }
            }

            
            var activity_id = null;
            var selectOverlayId = null;
            var tasks = [];
            //Generte div to indicate selected element on select
            var selectOverlayHtml = function (width, height) {
                return '<div style="width: 0; height: 0; border-top: 30px solid green; border-right: 30px solid transparent;"></div>';
            }
            //Click Event
            var selectElement = function (element) {
                if (selectOverlayId != null) {
                    overlays.remove(selectOverlayId);
                    selectOverlayId = null;/*SDG*/
                }

                if (element.type == 'bpmn:CallActivity' && clickableObjects[element.id] &&
                    clickableObjects[element.id].elementType == 'activity') {

                    nullOp();

                    self.selectedItemType = 'activity';
                    self.panelHeadingType = 'Activity';
                    self.panelHeadingName = element.id;
                    self.processVariables = processVariables;

                    var data = $filter('filter')(bpmnElements, { bpmn_id: element.id })[0];
                    self.activity_id = data.id;
                    self.person = person;
                    self.tasks = data.tasks;

                    //$log.log('data:' + JSON.stringify(self.tasks));
                    //$log.log('Activity Selected with:' + self.selectedItemType + ' Header:' + self.panelHeading);

                    selectOverlayId = overlays.add(element.id, {
                        position: { top: 2, left: 2 },
                        html: selectOverlayHtml()
                    });
                } else if (element.type == 'bpmn:IntermediateCatchEvent' && clickableObjects[element.id] &&
                        clickableObjects[element.id].elementType == 'timer') {

                    nullOp();

                    getTimerDuration(element.id);

                    self.selectedItemType = 'timer';
                    //self.panelHeading = element.id;
                    self.panelHeadingType = 'Timer';
                    self.panelHeadingName = element.id;

                    //$log.log('Activity Selected with:' + self.selectedItemType + ' Header:' + self.panelHeading);

                    selectOverlayId = overlays.add(element.id, {
                        position: { top: -10, right: 17 },
                        html: selectOverlayHtml()
                    });
                } else if (element.type == 'bpmn:Process') {
                    nullOp();

                    self.selectedItemType = 'process';
                    self.adminPersonId = adminPersonId;
                    //SDG self.selectedDate = new Date();
                    //self.panelHeading = '';/*SDG*/
                    self.panelHeadingType = 'Process';
                    self.panelHeadingName = element.id;
                    self.person = person;
                    self.processVariables = processVariables;

                    //$log.log(JSON.stringify(processVariables));
                    //$log.log('person_drop:' + JSON.stringify(self.person));
                } else if (element.type == 'bpmn:CallActivity' && clickableObjects[element.id] &&
                        clickableObjects[element.id].elementType == 'subprocessCall') {
                    nullOp();
                    self.selectedItemType = 'subprocessCall';
                    self.panelHeadingType = 'Sub-Process Call';
                    self.panelHeadingName = element.id;
                } else {
                    self.selectedItemType = '';
                    self.panelHeadingType = '';

                    nullOp();
                }
            }


            //Double Click Event
            var doubleClickElement = function (element) {
                $log.log('Called doubleClickElement()');
				var href = $window.location.href;
				var dtIdx = href.indexOf('bpmn?');
				var href = href.substring(0, dtIdx + 5);
				
				var selectedProcesses =$filter('filter')(bpmnElements, { bpmn_id: element.id });// bpmnElements.filter(function(d){return element.id == d.bpmn_id;})[0].name;
				if (selectedProcesses.length > 0) {
					var selectedProcess=selectedProcesses[0];
				
					if(selectedProcess.hasOwnProperty('name')) {
						var processName=selectedProcess.name;
						var newHref = href + 'project_id=' + self.projectId + '&phase_id=' + self.phaseId + '&process=' + processName;
						$window.location.href = newHref;
					}
				}
            }

            //Event Handler
            var eventHandler = function (event) {
                // e.element = the model element
                // e.gfx = the graphical element
                var element = event.element;
                switch (event.type) {
                    case "element.hover":
                        hoverElement(element);
                        break;
                    case "element.click":
                        selectElement(element);
                        break;
                    case "element.dblclick":
                        doubleClickElement(element);
                        break;
                }
            }

            events.forEach(function (event) {
                eventBus.on(event, eventHandler);
            });
            //--------------END EVENT BINDING-----------//

    
            //----------START TIMERS FUNCTIONALITY---------//

            //Get Timer Duration
            var getTimerDuration = function (timerVal) {
                var requestUrl = 'baf/TimerDurationFetch?phase_id=' + self.phaseId + '&bpmn_name=' + self.processName + '&timer_id=' + timerVal;
                $http.get(requestUrl).then(function (response) {
                    var timer = response.data;
                    if (timer != '') {
                        self.selectedItemType = 'timer';
                        self.eltype = timerVal;		// get timer type
                        self.timerduration = timer;
                    } else {
                        self.selectedItemType = '';
                    }
                })
            }

            //Set Timer Duration
            self.setTimerDuration = function (clickedOn, updatedTime) {
                var requestUrl = 'baf/TimerDurationSet/?' + "method=1&phase_id=" + self.phaseId + "&bpmn_name=" + self.processName + "&timer_id=" + clickedOn + "&duration=" + updatedTime;

                $http({ method: 'POST', url: requestUrl }).success(function (data) {
                    $log.log('Success....');
                }).error(function (data, status, headers, config) {
                    $log.log('Error....');
                });

            }

            //----------END TIMERS FUNCTIONALITY---------//


            //----------START ACTIVITY FUNCTIONALITY---------//

            //Set Selected Assignee To Dropdown on change
            self.TaskAssignee_SelectedIndexChanged = function (task, assigneeid, assigneefirstname, assigneelastname) {
                task.assignee._id = assigneeid;
                task.assignee.name = assigneefirstname + ' ' + assigneelastname;
            }

            //Set Assignee
            self.setTaskAssignee = function (task) {
                var requestUrl = 'baf/ActionContributorSet?' + "method=1&person_id=" + task.assignee._id + "&activity_id=" + self.activity_id + "&action_name=" + task.name + "&project_id=" + self.projectId;

                $http({ method: 'POST', url: requestUrl}).success(function (data) {
                    $log.log('Success....');
                }).error(function (data, status, headers, config) {
                    $log.log('Error....');
                });

            }

            //Update Task Duration
            self.setTaskDuration = function (task) {
                var requestUrl = 'baf/ActionDurationSet?activity_id=' + self.activity_id + '&duration=' + task.duration +
                  '&action_name=' + task.name;

                $http({ method: 'POST', url: requestUrl }).success(function (data) {
                    $log.log('Success....');
                }).error(function (data, status, headers, config) {
                    $log.log('Error....');
                });
            }

            //Add New Task/Action
            self.addActivityAction = function (activity_id, action_name, type) {
                var requestUrl = 'baf/ActionAdd?' + "method=1&activity_id=" + activity_id + "&action_name=" + action_name + "&type=" + type + "&bpmn_name=" + self.processName + "&assignee_id=" + adminPersonId;

                $http({ method: 'POST', url: requestUrl }).success(function (data) {
                    $log.log('Success....');
                }).error(function (data, status, headers, config) {
                    $log.log('Error....');
                });

            }

            //----------END ACTIVITY FUNCTIONALITY---------//


            //----------START PROCESS FUNCTIONALITY---------//
            var ManagerName = null;

            //Set Selected Phase Manager to dropdown on bind
            self.SelectedProcessPhaseManager = function (id) {
                var managerdata = $filter('filter')(person, { _id: id })[0];
                self.ManagerName = managerdata.first_name + ' ' + managerdata.last_name;
            }
            
            //Set Selected Phase Manager To Dropdown on change
            self.ProcessPhaseManager_SelectedIndexChanged = function (id, firstname, lastname) {
                self.adminPersonId = id;
                self.ManagerName = firstname + ' ' + lastname;
            }

            //Udate Phase Manager
            self.setProcessPhaseManager = function (person_id) {

                var requestUrl = 'baf/PhaseAdministratorSet?' + "method=1&phase_id=" + self.phaseId + "&person_id=" + person_id + "&project_id=" + self.projectId;

                $http({ method: 'POST', url: requestUrl }).success(function (data) {
                    $log.log('Success....');
                }).error(function (data, status, headers, config) {
                    $log.log('Error....');
                });

            }

            //Udate Start Date & Time
            self.setProcessStartDateTime = function (dt) {
                //SDG var timestamp = self.selectedDate.getTime(dt);
                /*SDG*/ var timestamp = self.processStartDatetime.getTime();
                var requestUrl = 'baf/PhaseStartDateTimeSet/?' + "method=1&phase_id=" + self.phaseId + "&datetime=" + timestamp;
                $log.log(requestUrl);

                $http({ method: 'POST', url: requestUrl }).success(function (data) {
                    $log.log('Success....');
                }).error(function (data, status, headers, config) {
                    $log.log('Error....');
                });

            }

            //Update Variable Value
            self.setProcessVariableValue = function (variable) {
                var requestUrl = 'baf/VariableValueSet/?' + "method=1&phase_id=" + self.phaseId + "&label=" + variable.label + "&bpmn_name=" + self.processName + "&value=" + variable.value;

                $http({ method: 'POST', url: requestUrl }).success(function (data) {
                    $log.log('Success....');
                }).error(function (data, status, headers, config) {
                    $log.log('Error....');
                });

            }

            //----------END PROCESS FUNCTIONALITY---------//
            
/*                          END                     */
}]);