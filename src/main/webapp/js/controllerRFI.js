angular.module('BuildWhizApp')

.controller("RFICtrl", ['$log', '$http', 'AuthenticationService', '$window', '$routeParams', '$sce',
    function ($log, $http, AuthService, $window, $routeParams, $sce) {

  var self = this;

  self.busy = false;
  self.showInfo = false;
  self.subject = '';
  self.text = '';
  self.messages = [];
  self.messageDetails = [];

  self.selectedMessage = null;

  self.rfiAttachments = [];

  self.toggleInfoDisplay = function() {
    self.showInfo = !self.showInfo;
    $log.log('Calling toggleInfoDisplay: ' + self.showInfo);
  }

  self.infoText = $sce.trustAsHtml('RFIs may be initiated and RFI responses on this page ... (Still under construction) ');

  self.submitRFI = function() {
    var query = 'baf/RFIMessageSubmit';
    var postData = {person_id: AuthService.data._id, text: self.text, rfi_id: self.selectedMessage._id};
    if (self.rfiAttachments.length > 0) {
       postData.attachments = self.rfiAttachments.map(function(a){return JSON.stringify(a);}).join('#');
    }
    $log.log('Calling POST ' + query);
    self.busy = true;
    $http.post(query, postData).then(
      function(resp) {
        self.text = '';
        self.rfiAttachments = [];
        $log.log('OK POST ' + query)
        self.busy = false;
        //alert('RFI Sent OK')
        self.selectMessage(self.selectedMessage);
      },
      function(resp) {
        $log.log('ERROR POST ' + query)
        self.busy = false;
        alert('ERROR sending RFI')
      }
    )
  }

  self.sendDisabled = function() {
    return self.subject == '' || self.text == '';
  }

  self.refreshRfiList = function(checkForId) {
    var query = 'baf/RFIMessagesFetch?person_id=' + AuthService.data._id + '&tz=' + AuthService.data.tz;
    $log.log('Calling GET ' + query);
    self.busy = true;
    $http.get(query).then(
      function(resp) {
        self.messages = resp.data;
        self.selectedMessage = null;
        self.messageDetails = [];
        self.subject = '';
        self.text = '';
        $log.log('OK GET ' + query)
        self.busy = false;
        if (checkForId && $routeParams.hasOwnProperty('rfi_id')) {
          var rfi_id = $routeParams.rfi_id;
          var theMessage = self.messages.filter(function(msg){return msg._id == rfi_id;});
          if (theMessage.length > 0) {
            self.selectMessage(theMessage[0]);
          }
        }
      },
      function(resp) {
        $log.log('ERROR GET ' + query)
        self.busy = false;
      }
    )
  }

  self.selectMessage = function(msg) {
    if (msg) {
      var query = 'baf/RFIDetailsFetch?person_id=' + AuthService.data._id + '&tz=' + AuthService.data.tz +
          '&rfi_id=' + msg._id;
      $log.log('Calling GET ' + query);
      self.busy = true;
      $http.get(query).then(
        function(resp) {
          self.messageDetails = resp.data;
          self.selectedMessage = msg;
          self.subject = msg.subject;
          $log.log('OK GET ' + query)
          self.busy = false;
       },
        function(resp) {
          self.selectedMessage = null;
          $log.log('ERROR GET ' + query)
          self.busy = false;
        }
      )
    } else {
      self.refreshRfiList();
    }
  }

  self.documentLabel = function(doc) {
    return [doc.document_info.category, doc.document_info.subcategory, doc.document_info.description].join('/')
  }

  self.attachFile = function() {
    $log.log('Called attachFile()');
    var attachButton = $window.document.getElementById('attach-file-button');
    attachButton.addEventListener('change', self.attachmentUploadBegin, false);
    attachButton.click();
    $log.log('Exiting attachFile()');
  }

  self.attachmentUploadBegin = function(evt) {
    $log.log('Called uploadBegin()');
    var attachButton = $window.document.getElementById('attach-file-button');
    attachButton.removeEventListener('change', self.uploadBegin, false);
    var files = evt.target.files; // FileList of File objects
    if (files.length > 0) {
      var timestamp = new Date().getTime();
      var formData = new FormData();
      angular.forEach(files, function(file, index) {
        formData.append(file.name, file, file.name);
        $log.log('formData.append(' + file.name + ')');
      });
      var desc = self.selectedMessage._id + '/' + self.selectedMessage.timestamp + '/' + timestamp;
      var query = 'baf/DocumentPreload?person_id=' + AuthService.data._id + '&timestamp=' + timestamp +
          '&author_person_id=' + AuthService.data._id + '&category=SYSTEM' + '&subcategory=RFI-Attachment' +
          '&name=' + escape(self.selectedMessage.subject) + '&description=' + escape(desc);
      $log.log("POST: " + query);
      self.busy = true;
      $http.post(query, formData, {transformRequest: angular.identity, headers: {'Content-Type': undefined}}).then(
        function(resp) {
          $log.log('OK ' + query);
          self.busy = false;
          self.rfiAttachments.push(resp.data[0]);
          alert('File attached OK');
          self.busy = false;
        },
        function() {
          $log.log('ERROR ' + query);
          self.busy = false;
          alert('ERROR: Uploading ' + files.length + ' files failed');
          self.busy = false;
        }
      )
    }
    $log.log('Exiting uploadBegin()');
  }

  self.closeMessage = function() {
    $log.log('Calling closeMessage()');
    var q = 'baf/RFIClose?rfi_id=' + self.selectedMessage._id;
     self.busy = true;
    $http.post(q).then(
      function(resp) {
        self.busy = false;
        self.selectedMessage.status = 'closed';
        $log.log('OK closeMessage()');
      },
      function(resp) {
        self.busy = false;
        $log.log('ERROR closeMessage()');
      }
    );
  }

  self.rfiColor = function(rfi) {
    return rfi.hasNewMessages ? 'GreenYellow' : (rfi.status == 'closed' ? 'LightGray' : 'white');
  }

  self.refreshRfiList(true);

}]);