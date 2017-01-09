angular.module('BuildWhizApp', ['ngRoute', 'ui.bootstrap'])

.config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/', {
        templateUrl: 'html/home.html',
        controller: 'HomeCtrl as homeCtrl'
    }).when('/documents', {
        templateUrl: 'html/manage-documents.html',
        controller: 'ManageDocumentsCtrl as manageDocsCtrl'
    }).when('/projects', {
        templateUrl: 'html/projects.html',
        controller: 'ProjectsCtrl as projectsCtrl'
    }).when('/tasks', {
        templateUrl: 'html/tasks.html',
        controller: 'TasksCtrl as tasksCtrl'
    }).when('/rfi', {
        templateUrl: 'html/rfi.html',
        controller: 'RFICtrl as rfiCtrl'
    }).when('/userProfile', {
        templateUrl: 'html/userProfile.html',
        controller: 'UserProfileCtrl as profileCtrl'
    }).when('/mongodb', {
        templateUrl: 'html/mongodb.html',
        controller: 'MongodbCtrl as mongoCtrl'
    }).when('/systemMonitor', {
        templateUrl: 'html/system-monitor.html',
        controller: 'SystemMonitorCtrl as sysMonCtrl'
    }).when('/manageUsers', {
        templateUrl: 'html/manage-users.html',
        controller: 'ManageUsersCtrl as usersCtrl'
    }).otherwise({redirectTo: '/'})
}])
