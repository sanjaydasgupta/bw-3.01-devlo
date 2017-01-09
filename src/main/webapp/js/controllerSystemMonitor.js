angular.module('BuildWhizApp')

.controller("SystemMonitorCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

  var self = this;

  self.dfh = [];

  var dfhQuery = 'etc/SystemMonitor?command=df -h'
  $log.log('Calling POST ' + dfhQuery)
  $http.post(dfhQuery).then(
    function(resp) {
      self.dfh = resp.data;
      $log.log('OK POST ' + dfhQuery + ', rows: ' + self.dfh.length)
    },
    function(resp) {
      $log.log('ERROR POST ' + dfhQuery)
    }
  );

}]);