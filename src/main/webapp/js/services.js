angular.module('BuildWhizApp')

.factory('AuthenticationService', ['$log', '$http', function ($log, $http) {
  return {
    login: function(email, password, client) {
      var url = 'baf/LoginPost?email=' + email + '&password=' + password;
      $log.log('POST ' + url);
      $http.post(url).then(
        function (response) {
          client.response = response;
          $log.log('Login OK: ' + response.data.first_name + ', ' + response.data.last_name)
          if (response.data.first_name.length != 0 && response.data.last_name.length != 0) {
            client.loggedIn = true;
            client.tryAgain = false;
            client.password = "";
          } else {
            $log.log('Login Failure: Authentication Failure');
            client.loggedIn = false;
            client.tryAgain = true;
          }
        },
        function(response) {
          $log.log('Login Error ' + response);
          client.loggedIn = false;
          client.tryAgain = true;
        }
      )
    },

    logout: function(client) {
        $log.log('Calling logout()')
        //$log.log('AuthService.loggedIn: ' + this.loggedIn)
        client.loggedIn = false;
        //$log.log('AuthService.loggedIn: ' + this.loggedIn)
    }
  };
}]);
