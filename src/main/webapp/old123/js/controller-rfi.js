angular.module('BuildWhizApp')

.controller("RFICtrl", ['$log', '$http', 'AuthenticationService', '$window', '$routeParams',
    function ($log, $http, AuthService, $window, $routeParams) {

  var self = this;

  self.busy = false;
  self.showInfo = false;
  self.subject = '';
  self.text = '';
  self.messages = [];
  self.messageDetails = [];

  self.selectedMessage = null;

  self.rfiAttachments = [];

//http://localhost:8080/bw-responsive-1.01/#/rfi?document_master_id=586ba72d92982d0a52ca267f&timestamp=1483498823235&
//origin0=documents-view2&origin1=Architecture&origin2=Sections&origin3=Sheet-4F

  if ($routeParams.hasOwnProperty('document_master_id') && $routeParams.hasOwnProperty('timestamp')) {
    self.document_master_id = $routeParams.document_master_id;
    self.timestamp = $routeParams.timestamp;
    if ($routeParams.hasOwnProperty('origin0')) {
      self.origin0 = $routeParams.origin0;
      if ($routeParams.hasOwnProperty('origin1')) {
        self.origin1 = $routeParams.origin1;
        if ($routeParams.hasOwnProperty('origin2')) {
          self.origin2 = $routeParams.origin2;
          if ($routeParams.hasOwnProperty('origin3')) {
            self.origin3 = $routeParams.origin3;
          }
        }
      }
    }
  }

  self.toggleInfoDisplay = function() {
    self.showInfo = !self.showInfo;
    $log.log('Calling toggleInfoDisplay: ' + self.showInfo);
  }

  self.displayNewMessage = function() {
    return !self.showInfo && (self.selectedMessage == null ||
        (self.selectedMessage != null && self.selectedMessage.status != 'closed'));
  }

  self.submitRFI = function() {
    var query = 'baf/RFIMessageSubmit';
    var postData = {person_id: AuthService.data._id, text: self.text};
    if (self.rfiAttachments.length > 0) {
       postData.attachments = self.rfiAttachments.map(function(a){return JSON.stringify(a);}).join('#');
    }
    if (self.selectedMessage == null) {
      postData.subject = self.subject;
      postData.document_id = self.document_master_id;
      postData.doc_version_timestamp = self.timestamp;
    } else {
      postData.rfi_id = self.selectedMessage._id;
    }
    $log.log('Calling POST ' + query);
    self.busy = true;
    $http.post(query, postData).then(
      function(resp) {
        self.subject = '';
        self.text = '';
        self.rfiAttachments = [];
        $log.log('OK POST ' + query)
        self.busy = false;
        //alert('RFI Sent OK')
        //self.selectMessage(self.selectedMessage);
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

  self.refreshRfiList = function() {
    var query = 'baf/RFIMessagesFetch?person_id=' + AuthService.data._id + '&tz=' + AuthService.data.tz;
    if ($routeParams.hasOwnProperty('document_master_id')) {
      query += '&document_master_id=' + self.document_master_id + '&timestamp=' + self.timestamp;
    }
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

  self.returnLink = function() {
    return '#/' + self.origin0 + '?origin1=' + self.origin1 + '&origin2=' + self.origin2 + '&origin3=' + self.origin3;
  }

  self.refreshRfiList();

}]);