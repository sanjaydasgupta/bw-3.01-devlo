angular.module('BuildWhizApp')

.controller("HomeCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

    var self = this;

    self.taskList = [];
    self.projectList = [];

}]);