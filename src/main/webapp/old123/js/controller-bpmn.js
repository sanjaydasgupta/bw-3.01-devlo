angular.module('BuildWhizApp')
    .controller("BpmnCtrl", ['$log', '$http', '$routeParams', '$sce', '$filter', '$window',
        function ($log, $http, $routeParams, $sce, $filter, $window) {

//          https://github.com/bpmn-io/bower-bpmn-js
//          https://github.com/bpmn-io/bpmn-js-examples/tree/master/interaction
//          https://github.com/bpmn-io/bpmn-js-examples/tree/master/overlay

            //--------START COMMON VARIABLES -------//
            var self = this;

            self.panelHeadingType = '';
            self.panelHeadingName = '';
            self.selectedItemType = '';
            self.selectedItem = null;

            self.bpmnName = $routeParams.process;
            self.projectId = $routeParams.project_id;
            self.projectName = $routeParams.project_name;
            self.processId = $routeParams.process_id;
            self.processName = $routeParams.process_name;
            self.isProjectManager = $routeParams.project_manager == 'true';
            self.isProcessManager = $routeParams.process_manager == 'true';

            self.newActionName = '';
            self.newActionType = null;

            self.processStartDatetime = new Date();

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

            $log.log('Process-Name: ' + self.processName);
            $log.log('Project-Id: ' + self.projectId);
            $log.log('Project-Name: ' + self.projectName);
            $log.log('Process-Id: ' + self.processId);
            $log.log('Process-Name: ' + self.processName);
            $log.log('Is-Project-Manager: ' + self.isProjectManager);
            $log.log('Is-Process-Manager: ' + self.isProcessManager);
            //--------END COMMON VARIABLES-------//

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
            self.persons = [];
            var requestUrl = 'api/Person';
            $http.get(requestUrl).then(function (response) {
                self.persons = response.data;
                //$log.log('person' + JSON.stringify(person));
            });

            var processTimers = [];
            var processVariables = [];
            var processActivities = [];
            var processCalls = [];
            var adminPersonId = null;
            var processStatus = null;

            var bpmnElements = [];
            var clickableObjects = {};

            var annotationOverlayHtml = function (bgColor, duration, onCriticalPath) {
                var borderInfo = onCriticalPath ? "3px solid red;" : "1px solid black;";
                return '<div style="background-color:' + bgColor + '; white-space:nowrap; padding:3px; ' +
                        ' border:' + borderInfo + ' border-radius:5px;' +
                        ' font-size: x-small; width:50px;">' + duration + '</div>';
            }

            //Initialize bpmn
            var initUrl = 'baf2/ProcessBpmnXml?bpmn_name=' + self.bpmnName + '&process_id=' + self.processId;
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
                          processStatus = response.data.process_status;

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
                    var onCriticalPath = bpmnElement.hasOwnProperty("on_critical_path") && bpmnElement.on_critical_path;
                    overlays.add(bpmnElement.bpmn_id, {
                        position: {
                            top: -30,
                            left: (bpmnElement.width - 50) / 2
                        },
                        html: annotationOverlayHtml(bgcolor, bpmnElement.duration, onCriticalPath)
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

                if (clickableObjects[element.id] && clickableObjects[element.id].elementType == 'activity') {

                    nullOp();
                    self.selectedItemType = 'activity';
                    self.panelHeadingType = 'Activity';
                    self.panelHeadingName = element.id;

                    self.selectedItem = clickableObjects[element.id];

                    //$log.log('data:' + JSON.stringify(self.tasks));
                    //$log.log('Activity Selected with:' + self.selectedItemType + ' Header:' + self.panelHeading);

                    selectOverlayId = overlays.add(element.id, {
                        position: { top: 2, left: 2 },
                        html: selectOverlayHtml()
                    });
                } else if (clickableObjects[element.id] && clickableObjects[element.id].elementType == 'timer') {

                    nullOp();

                    self.selectedItemType = 'timer';
                    self.panelHeadingType = 'Timer';
                    self.panelHeadingName = element.id;

                    self.selectedItem = clickableObjects[element.id];

                    selectOverlayId = overlays.add(element.id, {
                        position: { top: 2, left: 2 },
                        html: selectOverlayHtml()
                    });
                } else if (element.type == 'bpmn:Process') {
                    nullOp();

                    self.selectedItemType = 'process';
                    self.adminPersonId = adminPersonId;
                    self.panelHeadingType = 'Process';
                    self.panelHeadingName = element.id;
                    //self.person = person;
                    self.processVariables = processVariables;

                    //$log.log(JSON.stringify(processVariables));
                    //$log.log('person_drop:' + JSON.stringify(self.person));
                } else if (element.type == 'bpmn:CallActivity' && clickableObjects[element.id] &&
                        clickableObjects[element.id].elementType == 'subprocessCall') {
                    nullOp();
                    self.selectedItemType = 'subprocessCall';
                    self.panelHeadingType = 'Sub-Process Call';
                    self.panelHeadingName = element.id;
                    selectOverlayId = overlays.add(element.id, {
                        position: { top: 2, left: 2 },
                        html: selectOverlayHtml()
                    });
                } else {
                    self.selectedItemType = '';
                    self.panelHeadingType = '';

                    nullOp();
                }
            }

            //Double Click Event
            var doubleClickElement = function (element) {
                $log.log('Called doubleClickElement()');

				var selectedProcesses = bpmnElements.filter(function(d){return element.id == d.bpmn_id;});
				if (selectedProcesses.length > 0) {
					var selectedProcess = selectedProcesses[0];

					if(selectedProcess.hasOwnProperty('name') && selectedProcess.elementType == 'subprocessCall') {
						var processName = selectedProcess.name;
				        var dtIdx = $window.location.href.indexOf('bpmn?');
				        var href = $window.location.href.substring(0, dtIdx + 5);
						var newHref = href + 'project_id=' + self.projectId + '&process_id=' + self.processId + '&process=' + processName;
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

            //Set Timer Duration
            self.setTimerDuration = function () {
                var requestUrl = 'baf/TimerDurationSet/?' + 'process_id=' + self.processId +
                '&bpmn_name=' + self.processName + '&timer_id=' + self.selectedItem.id +
                "&duration=" + self.selectedItem.duration;

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
                var requestUrl = 'baf/ActionContributorSet?' + "method=1&person_id=" + task.assignee._id +
                    "&activity_id=" + self.selectedItem.id + "&action_name=" + task.name + "&project_id=" + self.projectId;

                $http({ method: 'POST', url: requestUrl}).success(function (data) {
                    $log.log('Success....');
                }).error(function (data, status, headers, config) {
                    $log.log('Error....');
                });

            }

            //Update Task Duration
            self.setTaskDuration = function (task) {
                var requestUrl = 'baf/ActionDurationSet?activity_id=' + self.selectedItem.id + '&duration=' + task.duration +
                  '&action_name=' + task.name;

                $http({ method: 'POST', url: requestUrl }).success(function (data) {
                    $log.log('Success....');
                }).error(function (data, status, headers, config) {
                    $log.log('Error....');
                });
            }

            //Add New Task/Action
            self.addActivityAction = function () {
                var requestUrl = 'baf/ActionAdd?' + "method=1&activity_id=" + self.selectedItem.id +
                    "&action_name=" + self.newActionName + "&type=" + self.newActionType.toLowerCase() +
                    "&bpmn_name=" + self.processName + "&assignee_id=" + adminPersonId;

                $http({ method: 'POST', url: requestUrl }).success(function (data) {
                    self.newActionName = '';
                    self.newActionType = null;
                    $log.log('Success....');
                }).error(function (data, status, headers, config) {
                    $log.log('Error....');
                });
            }

            self.actionControlsDisabled = function() {
                var rv = !((self.selectedItem.status == 'defined') && (self.isProcessManager || self.isProjectManager));
                $log.log('returns: ' + rv);
                return rv;
            }

            //----------END ACTIVITY FUNCTIONALITY---------//


            //----------START PROCESS FUNCTIONALITY---------//
            var ManagerName = null;

            self.processControlsDisabled = function() {
                var rv = !((processStatus == 'defined') && (self.isProcessManager || self.isProjectManager));
                $log.log('returns: ' + rv);
                return rv;
            }

            //Set Selected Process Manager to dropdown on bind
            self.SelectedProcessProcessManager = function (id) {
                var managerdata = $filter('filter')(self.persons, { _id: id })[0];
                self.ManagerName = managerdata.first_name + ' ' + managerdata.last_name;
            }
            
            //Set Selected Process Manager To Dropdown on change
            self.ProcessProcessManager_SelectedIndexChanged = function (id, firstname, lastname) {
                self.adminPersonId = id;
                self.ManagerName = firstname + ' ' + lastname;
            }

            //Udate Process Manager
            self.setProcessProcessManager = function (person_id) {

                var requestUrl = 'baf2/ProcessAdministratorSet?' + "method=1&process_id=" + self.processId + "&person_id=" + person_id + "&project_id=" + self.projectId;
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
                var requestUrl = 'baf2/ProcessStartDateTimeSet/?' + "method=1&process_id=" + self.processId + "&datetime=" + timestamp;
                $log.log(requestUrl);

                $http({ method: 'POST', url: requestUrl }).success(function (data) {
                    $log.log('Success....');
                }).error(function (data, status, headers, config) {
                    $log.log('Error....');
                });

            }

            //Update Variable Value
            self.setProcessVariableValue = function (variable) {
                var requestUrl = 'baf/VariableValueSet/?' + "method=1&process_id=" + self.processId + "&label=" + variable.label + "&bpmn_name=" + self.processName + "&value=" + variable.value;

                $http({ method: 'POST', url: requestUrl }).success(function (data) {
                    $log.log('Success....');
                }).error(function (data, status, headers, config) {
                    $log.log('Error....');
                });

            }

            //----------END PROCESS FUNCTIONALITY---------//
            
/*                          END                     */
}]);