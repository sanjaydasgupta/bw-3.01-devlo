angular.module('BuildWhizApp')

.controller("SystemMonitorCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.output = [];

  self.monitor = function(command) {
    var dfhQuery = 'etc/SystemMonitor?command=' + command;
    $log.log('Calling POST ' + dfhQuery)
    $http.post(dfhQuery).then(
      function(resp) {
        self.output = resp.data;
        $log.log('OK POST ' + dfhQuery + ', rows: ' + self.output.length)
      },
      function(resp) {
        $log.log('ERROR POST ' + dfhQuery)
      }
    );
  }

  self.bgColor = function(row) {
    var color = 'white'
    var patterns = ['/', '/home', 'mongodb', 'java'];
    patterns.forEach(function(pattern) {
      row.forEach(function(rowElement) {
        if (rowElement == pattern) {
          color = 'yellow';
        }
      })
    });
    return color;
  }

}]);