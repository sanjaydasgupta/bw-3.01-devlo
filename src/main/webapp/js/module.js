var app = angular.module('BuildWhizNgApp', ['ngRoute', 'ui.bootstrap', 'ui.tab.scroll']);
//var app = angular.module('BuildWhizNgApp', ['cam.commons', 'ngRoute', 'ui.bootstrap', 'ui.tab.scroll']);



app.config(function (scrollableTabsetConfigProvider) {

    scrollableTabsetConfigProvider.setShowTooltips(true);
    scrollableTabsetConfigProvider.setTooltipLeft('right');
    scrollableTabsetConfigProvider.setTooltipRight('left');
    scrollableTabsetConfigProvider.setScrollLeftIcon('glyphicon glyphicon-chevron-left');
    scrollableTabsetConfigProvider.setScrollRightIcon('glyphicon glyphicon-chevron-right');

});




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
            .when('/bpmnViewer', {
                templateUrl: 'bpmnViewer.html',
                controller: 'BpmnViewerCtrl'
                })
            .when('/userProfile', {
                templateUrl: 'userProfile.html',
                controller: 'UserProfileCtrl'
                })
            .when('/documents', {
                templateUrl: 'documents.html',
                controller: 'DocumentsCtrl'
                })
            .otherwise({
                redirectTo: '/login'
            });



    $locationProvider.html5Mode(false).hashPrefix('!'); // This is for Hashbang Mode

}]);


























