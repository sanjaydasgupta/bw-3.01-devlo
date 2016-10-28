﻿angular.module('BuildWhizApp')

.controller("UserProfileCtrl", ['$log', '$http', 'AuthenticationService', function ($log, $http, AuthService) {

    var self = this;

    self.userPasswordSetDisabled = function() {
        return !self.oldPassword || !self.newPassword || !self.cnfPassword || (self.newPassword != self.cnfPassword);
    }

    self.userPasswordSet = function() {
        var sPersonID = AuthService.data._id;
        var query = '?old_password=' + self.oldPassword + '&new_password=' + self.newPassword + '&person_id=' + sPersonID;
        $log.log('Calling /baf/UserPasswordSet' + query);
        $http.post('baf/UserPasswordSet' + query).then(
            function() {
                self.oldPassword = '';
                self.newPassword = '';
                self.cnfPassword = '';
            },
            function() {
                self.oldPassword = '';
                self.newPassword = '';
                self.cnfPassword = '';
                alert('Failed to set password');
            }
        )
    }

}]);