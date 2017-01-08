angular.module('BuildWhizApp')

.controller("RFICtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.subject = '';
  self.text = '';
  self.messages = [];

  self.newRfiMessage = function() {
    var query = 'baf/RFIMessageSubmit?person_id=' + AuthService.data._id +
        '&text=' + self.text + '&subject=' + self.subject;
    $log.log('Calling POST ' + query);
    $http.post(query).then(
      function(resp) {
        $log.log('OK POST ' + query)
      },
      function(resp) {
        $log.log('ERROR POST ' + query)
      }
    )
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

  self.refreshRfiList();

}]);