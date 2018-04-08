angular.module('BuildWhizApp', ['ngRoute', 'ui.bootstrap'])

.config(['$routeProvider', function($routeProvider) {
    $routeProvider.when('/', {
        templateUrl: 'bw/home.html',
        controller: 'HomeCtrl as homeCtrl'
    }).when('/systemMonitor', {
        templateUrl: 'bw/system-monitor.html',
        controller: 'SystemMonitorCtrl as sysMonCtrl'
    }).when('/mongodb', {
        templateUrl: 'bw/mongodb.html',
        controller: 'MongodbCtrl as mongoCtrl'
    /*}).when('/documents-loader', {
        templateUrl: 'html/documents-loader.html',
        controller: 'LoaderDocumentsCtrl as loaderCtrl'
    }).when('/documents-view2', {
        templateUrl: 'html/documents-view2.html',
        controller: 'ViewDocumentsCtrl2 as viewDocsCtrl2'
    }).when('/projects', {
        templateUrl: 'html/projects.html',
        controller: 'ProjectsCtrl as projectsCtrl'
    }).when('/tasks', {
        templateUrl: 'html/tasks.html',
        controller: 'TasksCtrl as tasksCtrl'
    }).when('/rfi', {
        templateUrl: 'html/rfi.html',
        controller: 'RFICtrl as rfiCtrl'
    }).when('/status-update', {
        templateUrl: 'html/status-update.html',
        controller: 'StatusUpdateCtrl as statusCtrl'
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
    }).when('/help', {
        templateUrl: 'html/help.html',
        controller: 'HelpCtrl as helpCtrl'
    }).when('/role-mapping', {
        templateUrl: 'html/role-mapping.html',
        controller: 'RoleCtrl as roleCtrl'
    }).when('/bpmn', {
        templateUrl: 'html/bpmn.html',
        controller: 'BpmnCtrl as bpmnCtrl'
    */}).otherwise({redirectTo: '/'})
}])
