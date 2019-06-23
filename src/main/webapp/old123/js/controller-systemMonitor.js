angular.module('BuildWhizApp')

.controller("SystemMonitorCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.output = [];
  self.commandName = '?';

  self.monitor = function(command, name) {
    var dfhQuery = '/bw-dot-2.01/etc/SystemMonitor?command=' + command;
    $log.log('Calling POST ' + dfhQuery)
    $http.post(dfhQuery).then(
      function(resp) {
        self.output = resp.data;
        self.commandName = name;
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