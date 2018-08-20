angular.module('BuildWhizApp')

.factory('AuthenticationService', ['$log', '$http', '$document', '$location',
    function ($log, $http, $document, $location) {
  return {
    loggedIn: false,
    //tryAgain: false,
    data: null,
    loginClient: null,

    login: function(theEmail, thePassword, client) {
      var self = this;
      var url = 'etc/LoginPost';
      var postData = {email: theEmail, password: thePassword};
      var location = $location.url();
      $http.post(url, postData).then(
        function (response) {
          if (response.data.first_name.length != 0 && response.data.last_name.length != 0) {
            $log.log('AuthenticationService: OK, data=' + response.data.first_name + ', ' + response.data.last_name)
            self.data = response.data;
            self.loggedIn = true;
            client.data = response.data;
            client.loggedIn = true;
            client.tryAgain = false;
            client.password = '';
            loginClient = client;
            $location.url(location);
          } else {
            $log.log('AuthenticationService: FAILURE');
            self.loggedIn = false;
            client.loggedIn = false;
            client.tryAgain = true;
          }
        },
        function(response, client) {
          $log.log('Login Error ' + response);
          self.loggedIn = false;
          self.data = null;
          client.loggedIn = false;
          client.data = null;
          client.tryAgain = true;
        }
      )
    },

    logout: function() {
      $log.log('AuthService logout()')
      this.loggedIn = false;
      this.data = null;
      loginClient.loggedIn = false;
      loginClient.data = null;
      var url = 'etc/LoginPost'; // Log OUT
      $http.post(url)
    },

    setupLoginData: function(data, client) {
      var self = this;
      self.data = data;
      self.loggedIn = true;
      client.data = data;
      client.loggedIn = true;
      client.tryAgain = false;
      client.password = '';
      loginClient = client;
      $log.log('Called setupLoginData()');
    }

  };
}]);
