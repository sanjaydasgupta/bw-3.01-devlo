angular.module('BuildWhizApp')

.controller("SystemMonitorCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.output = [];
  self.commandName = '?';

  self.monitor = function(command, name) {
    var dfhQuery = '/bw-dot-2.01/etc/SystemMonitor?command=' + command;
    $log.log('Calling GET ' + dfhQuery)
    $http.get(dfhQuery).then(
      function(resp) {
        self.output = resp.data;
        self.commandName = name;
        $log.log('OK GET ' + dfhQuery + ', rows: ' + self.output.length)
      },
      function(resp) {
        $log.log('ERROR GET ' + dfhQuery)
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