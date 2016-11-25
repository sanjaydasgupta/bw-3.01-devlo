angular.module('BuildWhizApp')

.controller("TasksCtrl", ['$log', '$http', '$window', 'AuthenticationService',
    function ($log, $http, $window, AuthService) {

  var self = this;

  self.taskList = [];
  self.selectedTask = null;
  self.taskSelected = false;
  self.confirmingCompletion = false;
  self.progressReportAttachments = [];
  self.currentFilterKey = 'All';
  self.userNameEmail = 'abc@buildwhiz.com';
  self.actionCompletionText = '';

  self.select = function(task) {
    if (task) {
      self.selectedTask = task;
      self.taskSelected = true;
      var message = 'TasksCtrl: selected ' + task.name + ' (activity: ' + task.activity_id + ') selected';
      $log.log(message)
    } else {
      self.selectedTask = null;
      self.taskSelected = false;
    }
  }

  self.fetchActions = function(filter) {
    var filterKey = filter ? filter : 'All';
    var query = 'baf/OwnedActionsSummary?person_id=' + AuthService.data._id + '&filter_key=' + filterKey.toLowerCase();
    $log.log('TasksCtrl: GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.taskList = resp.data;
        self.currentFilterKey = filterKey;
        $log.log('OK-TasksCtrl: got ' + self.taskList.length + ' objects');
        self.select(); // select none
      },
      function(errResponse) {alert("TasksCtrl: ERROR(collection-details): " + errResponse);}
    );
  }

  self.submitProgressReport = function() {
    var formData = new FormData();
    angular.forEach(self.progressReportAttachments, function(file, index) {
      formData.append(file.name, file, file.name);
      $log.log('formData.append(name: ' + file.name + ', stringified: ' + JSON.stringify(file) + ')');
    });
    formData.append('end-marker', 'end-marker-content');
    var query = 'baf/ProgressReportSubmit?person_id=' + AuthService.data._id;
    $http.post(query, formData, {transformRequest: angular.identity, headers: {'Content-Type': undefined}}).then(
      function() {
        $log.log('OK submitProgressReport')
      },
      function() {
        $log.log('ERROR submitProgressReport')
      }
    )
  }

  self.handleFile = function(evt) {
    var files = evt.target.files; // FileList of File objects
    if (files.length > 0) {
      var file = files[0];
      var fileDescription = 'name: ' + file.name + ', type: ' + file.type + ', size: ' + file.size;
      $log.log('File: ' + fileDescription);
      self.progressReportAttachments.push(file);
    }
  }

  self.captureFile = function() {
    var fileButton = $window.document.getElementById('file-button');
    $log.log('BEFORE File-Button addEventListener: ' + fileButton);
    fileButton.addEventListener('change', self.handleFile, false);
    fileButton.click();
    $log.log('AFTER File-Button click: ' + fileButton);
  }

  self.handlePhoto = function(evt) {
    var files = evt.target.files; // FileList of File objects
    if (files.length > 0) {
      var photo = files[0];
      var photoDescription = 'name: ' + photo.name + ', type: ' + photo.type + ', size: ' + photo.size;
      $log.log('Photo: ' + photoDescription);
      self.progressReportAttachments.push(photo);
    }
  }

  self.takePhoto = function() {
      var cameraButton = $window.document.getElementById('camera-button');
      $log.log('BEFORE Camera-Button addEventListener: ' + cameraButton);
      cameraButton.addEventListener('change', self.handlePhoto, false);
      cameraButton.click();
      $log.log('AFTER Camera-Button click: ' + cameraButton);
  }

  self.userNameEmailFromCookie = function() {
    $log.log('TasksCtrl: userNameEmailFromCookie() Called');
    $http.get('baf/UserNameEmailFromCookie').then(
      function(resp) {
        self.userNameEmail = resp.data;
        $log.log('TasksCtrl: userNameEmailFromCookie(): ' + self.userNameEmail);
      },
      function(errResponse) {alert("TasksCtrl: ERROR(userNameEmailFromCookie()): " + errResponse);}
    );
  }

  self.actionComplete = function() {
    $log.log('TasksCtrl: taskComplete() Called');
    var query = 'baf/ActionComplete?activity_id=' + self.selectedTask.activity_id +
        '&action_name=' + self.selectedTask.name + '&completion_message=' + self.actionCompletionText +
        '&review_ok=' + (self.selectedTask.reviewOk ? 'OK' : 'Not-Ok');
    $log.log('TasksCtrl: taskComplete() POST ' + query);
    $http.post(query).then(
      function(resp) {
        self.confirmingCompletion = false;
        self.actionCompletionText = '';
        self.fetchActions(self.currentFilterKey);
        $log.log('TasksCtrl: taskComplete(): OK');
      },
      function(errResponse) {alert("TasksCtrl: ERROR(taskComplete()): " + errResponse);}
    );
  }

  self.fetchActions();
  self.userNameEmailFromCookie();

}]);