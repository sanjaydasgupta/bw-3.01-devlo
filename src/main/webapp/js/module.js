angular.module('BuildWhizApp', ['ngRoute', 'ui.bootstrap'])

.config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/', {
        templateUrl: 'html/home.html',
        controller: 'HomeCtrl as homeCtrl'
    }).when('/projects', {
        templateUrl: 'html/projects.html',
        controller: 'ProjectsCtrl as projectsCtrl'
    }).when('/tasks', {
        templateUrl: 'html/tasks.html',
        controller: 'TasksCtrl as tasksCtrl'
    }).when('/rfi', {
        template: '<h2>RFI Route</h2>'
    }).when('/userProfile', {
        templateUrl: 'html/userProfile.html',
        controller: 'UserProfileCtrl as profileCtrl'
    }).when('/mongodb', {
        templateUrl: 'html/mongodb.html',
        controller: 'MongodbCtrl as mongoCtrl'
    }).otherwise({redirectTo: '/'})
}])
