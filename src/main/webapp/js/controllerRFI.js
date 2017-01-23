angular.module('BuildWhizApp')

.controller("RFICtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.subject = '';
  self.text = '';
  self.messages = [];
  self.messageDetails = [];

  self.selectedMessage = null;

  self.submitRFI = function() {
    var query = 'baf/RFIMessageSubmit?person_id=' + AuthService.data._id + '&text=' + self.text;
    if (self.selectedMessage != null) {
      query += '&rfi_id=' + self.selectedMessage._id;
    } else {
      query += '&subject=' + self.subject;
    }
    $log.log('Calling POST ' + query);
    $http.post(query).then(
      function(resp) {
        self.text = '';
        if (self.selectedMessage == null) {
          self.subject = '';
        }
        $log.log('OK POST ' + query)
      },
      function(resp) {
        $log.log('ERROR POST ' + query)
      }
    )
  }

  self.sendDisabled = function() {
    return self.subject == '' || self.text == '';
  }

  self.refreshRfiList = function() {
    var query = 'baf/RFIMessagesFetch?person_id=' + AuthService.data._id + '&tz=' + AuthService.data.tz;
    $log.log('Calling GET ' + query);
    $http.get(query).then(
      function(resp) {
        self.messages = resp.data;
        $log.log('OK GET ' + query)
      },
      function(resp) {
        $log.log('ERROR GET ' + query)
      }
    )
  }

  self.selectMessage = function(msg) {
    if (msg) {
      var query = 'baf/RFIDetailsFetch?person_id=' + AuthService.data._id + '&tz=' + AuthService.data.tz +
          '&rfi_id=' + msg._id;
      $log.log('Calling GET ' + query);
      $http.get(query).then(
        function(resp) {
          self.messageDetails = resp.data;
          self.selectedMessage = msg;
          self.subject = msg.subject;
          $log.log('OK GET ' + query)
        },
        function(resp) {
          self.selectedMessage = null;
          $log.log('ERROR GET ' + query)
        }
      )
    } else {
      self.selectedMessage = null;
      self.subject = '';
    }
  }

  self.documentLabel = function(doc) {
    return [doc.document_info.category, doc.document_info.subcategory, doc.document_info.description].join('/')
  }

  self.refreshRfiList();

}]);