var app = angular.module('BuildWhizNgApp', ['ngRoute','ui.bootstrap']);


//app.controller('MainCtrl', function ($scope) { });
app.config(['$routeProvider', '$locationProvider', function ($routeProvider, $locationProvider) {
    
    $routeProvider 
            .when('/login', {
                templateUrl: 'logIn.html',
                controller: 'BuildWhizNgAppLogInCtrl'
            })
            .when('/dashboard', {
                templateUrl: 'dashboard.html',
                controller: 'BuildWhizNgAppDashboardCtrl'
            })
            .when('/projects', {
                templateUrl: 'project.html',
                controller: 'BuildWhizNgAppProjectCtrl'
                })
            .when('/mongodb', {
                templateUrl: 'mongodb.html',
                controller: 'BuildWhizNgAppMongodbCtrl'
                })
            .when('/nextGen', {
                templateUrl: 'nextGen.html',
                controller: 'BuildWhizNgAppNextGenCtrl'
                })
            .when('/userProfile', {
                templateUrl: 'userProfile.html',
                controller: 'BuildWhizNgAppUserProfileCtrl'
                })
            .otherwise({
                redirectTo: '/login'
            });
    $locationProvider.html5Mode(false).hashPrefix('!'); // This is for Hashbang Mode

}]);


























