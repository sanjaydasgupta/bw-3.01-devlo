app.controller("BpmnViewerCtrl", function ($scope, $http, $log) {

    $http.get('baf/PhaseBpmnNamesFetch').then(
        function (response) {
            $scope.phaseNames = response.data;
        }
    );

    $scope.handleClick = function(element) {
        if(element.businessObject.$instanceOf('bpmn:FlowNode')) {
            if($scope.control.isHighlighted(element.id)) {
                $scope.control.clearHighlight(element.id);
                $scope.control.removeBadges(element.id);
                $scope.selectedNodes.splice($scope.selectedNodes.indexOf(element.id),1);
                $scope.$apply();
            } else {
                $scope.control.highlight(element.id);
                $scope.control.createBadge(element.id, {text: 'Test', tooltip: 'This is a tooltip'});
                $scope.selectedNodes.push(element.id);
                $scope.$apply();
            }
        }
    };

    $scope.control = {};
    $scope.selectedNodes = [];
    $scope.hovering = [];

    $scope.mouseEnter = function(element) {
        $scope.hovering.push(element.id);
        $scope.$apply();
    };

    $scope.mouseLeave = function(element) {
        $scope.hovering.splice($scope.hovering.indexOf(element.id), 1);
        $scope.$apply();
    };

    $scope.phaseSelected = function () {
        var param = '?bpmn_name=' + $scope.selectedPhaseName;
        $http.get('baf/PhaseBpmnXml' + param).then(
            function (response) {
                $scope.control = {};
                $scope.selectedNodes = [];
                $scope.diagramXML = response.data.bpmn20Xml;
            }
        );
    }

});