angular.module('BuildWhizApp', ['ngRoute', 'ui.bootstrap'])

.config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/', {
        template: '<h2>Home Route</h2>'
    }).when('/projects', {
        template: '<h2>Projects Route</h2>'
    }).when('/rfi', {
        template: '<h2>RFI Route</h2>'
    }).when('/userProfile', {
        templateUrl: 'html/userProfile.html',
        controller: 'UserProfileCtrl as profileCtrl'
    }).otherwise({redirectTo: '/'})
}])
