angular.module('ui.tab.scroll', [])
.provider('scrollableTabsetConfig', function(){

    //the default options
    var defaultConfig = {
      showTooltips: true,

      tooltipLeft: 'bottom',
      tooltipRight: 'bottom',

      //select the innermost child that isn't a span
      //this way we cover getting <tab-heading> and <tab heading=''>
      //but leave other markup out of it, unless it's a span (commonly an icon)
      tooltipTextSelector: '*:not(:has("*:not(span)"))',

      scrollLeftIcon: 'glyphicon glyphicon-chevron-left',
      scrollRightIcon: 'glyphicon glyphicon-chevron-right'
    };

    var config = angular.extend({}, defaultConfig);

    return {
      setShowTooltips : function(value){
        config.showTooltips = value;
      },
      setTooltipLeft : function(value){
        config.tooltipLeft = value;
      },
      setTooltipRight : function(value){
        config.tooltipRight = value;
      },
      setTooltipTextSelector : function(value){
        config.tooltipTextSelector = value;
      },
      setScrollLeftIcon : function(value){
        config.scrollLeftIcon = value;
      },
      setScrollRightIcon : function(value){
        config.scrollRightIcon = value;
      },
      $get: function(){
        return {
                  showTooltips: config.showTooltips,
                  tooltipLeft: config.tooltipLeft,
                  tooltipRight: config.tooltipRight,
                  tooltipTextSelector: config.tooltipTextSelector,
                  scrollLeftIcon: config.scrollLeftIcon,
                  scrollRightIcon: config.scrollRightIcon
                };
      }
    };
  }
)
.directive('scrollableTabset', [
  'scrollableTabsetConfig', '$window', '$interval', '$timeout',
  function(scrollableTabsetConfig, $window, $interval, $timeout) {

    var timeoutId = null;

    var cancelId = function() {
        if(timeoutId) {
          $interval.cancel(timeoutId);
          timeoutId = null;
        }
    };

    var bindHoldFunctionTo = function(element, fn) {

      //get rid of the previous scroll function
      element.unbind('mousedown');
      element.unbind('mouseup');

      var isHolding = false;

      var mouseDown = function() {
        isHolding = true;

        fn();

        timeoutId = $interval(function() {
          if(isHolding) {
            fn();

            if(element.is(':disabled')) {
              cancelId();
            }
          }
        }, 100);
      };

      var mouseUp = function() {
        isHolding = false;
        cancelId();
      };

      element.on('mousedown', mouseDown);
      element.on('mouseup', mouseUp);
    };

    return {
      restrict: 'AE',
      transclude: true,

      scope: {
        showTooltips: '=',
        tooltipLeft: '=',
        tooltipRight: '=',
        tooltipTextSelector: '=',
        scrollLeftIcon: '=',
        scrollRightIcon: '='
      },

      template: [
        '<div class="ui-tabs-scrollable">',
          '<button type="button"  class="btn nav-button left-nav-button" tooltip-placement="{{tooltipLeftDirection()}}" tooltip-html-unsafe="{{tooltipLeftContent()}}">',
            '<span class="{{scrollLeftIconClass()}}"></span>',
          '</button>',
          '<div class="spacer"  ng-transclude></div>',
          '<button type="button"  class="btn nav-button right-nav-button" tooltip-placement="{{tooltipRightDirection()}}" tooltip-html-unsafe="{{tooltipRightContent()}}">',
            '<span class="{{scrollRightIconClass()}}"></span>',
          '</button>',
        '</div>'
        ].join(''),

      link: function($scope, $el) {

        $scope.toTheLeftHTML = '';
        $scope.toTheRightHTML = '';

        var showTooltips = angular.isDefined($scope.showTooltips)? $scope.showTooltips : scrollableTabsetConfig.showTooltips;

        $scope.disableLeft = function() {
          return !$scope.toTheLeftHTML;
        };

        $scope.disableRight = function() {
          return !$scope.toTheRightHTML;
        };

        $scope.tooltipLeftContent = function() {
          return showTooltips ? $scope.toTheLeftHTML : '';
        };

        $scope.tooltipRightContent = function() {
          return showTooltips ? $scope.toTheRightHTML : '';
        };

        $scope.tooltipLeftDirection = function() {
          return $scope.tooltipLeft ? $scope.tooltipLeft : scrollableTabsetConfig.tooltipLeft;
        };

        $scope.tooltipRightDirection = function() {
          return $scope.tooltipRight ? $scope.tooltipRight : scrollableTabsetConfig.tooltipRight;
        };

        $scope.scrollLeftIconClass = function() {
          return $scope.scrollLeftIcon ? $scope.scrollLeftIcon : scrollableTabsetConfig.scrollLeftIcon;
        };

        $scope.scrollRightIconClass = function() {
          return $scope.scrollRightIcon ? $scope.scrollRightIcon : scrollableTabsetConfig.scrollRightIcon;
        };


        $scope.getSelector = function() {
          return $scope.tooltipTextSelector ? $scope.tooltipTextSelector : scrollableTabsetConfig.tooltipTextSelector;
        };

        $scope.toTheLeft = function() {
          if(!$scope.tabContainer) return;

          var nodes = [];
          $scope.tabContainer.find($scope.getSelector()).each(function(index, node) {

            var nodeObj = $(node);
            var nodeContainer = nodeObj.parentsUntil('ul');

            if(nodeContainer.offset().left > $scope.baseLeft) return;

            var html = nodeObj.html().trim();
            if(html) {
              nodes.push(html);
            }

          });

          $scope.toTheLeftHTML = nodes.join('<br>');
        };

        $scope.toTheRight = function() {
          if(!$scope.tabContainer) return;

          var nodes = [];
          $scope.tabContainer.find($scope.getSelector()).each(function(index, node) {

            var nodeObj = $(node);
            var nodeContainer = nodeObj.parentsUntil('ul');
            var nodeWidth = nodeContainer.offset().left;

            if(nodeWidth < $scope.tabWidth + $scope.baseLeft) return;

            var html = nodeObj.html().trim();
            if(html) {
              nodes.push(html);
            }

          });

          $scope.toTheRightHTML = nodes.join('<br>');
        };

        $scope.recalcSides = function() {
          $scope.toTheLeft();
          $scope.toTheRight();
        };

        var generateScrollFunction = function(el, offset) {
          return function() {
            el.scrollLeft += offset;
            $scope.recalcSides();
          };
        };

        var init = function() {
          var $leftNav = $el.find('.left-nav-button');
          var $rightNav = $el.find('.right-nav-button');
          var $tabs = $scope.tabContainer = $el.find('.spacer').find('ul.nav.nav-tabs');

          $scope.baseLeft = $el.offset().left;

          var tabContainerWidth = $scope.tabContainerWidth = $tabs[0].scrollWidth;
          var tabWidth = $scope.tabWidth = $tabs.width();
          var tabScrollWidth = tabWidth / 6;
          var realTabs = $tabs[0];

          $scope.hideButtons = tabContainerWidth === tabWidth;
          //alert("$scope.hideButtons=>" + $scope.hideButtons);
          //alert("tabContainerWidth" + tabContainerWidth);
          //alert("tabWidth=>" + tabWidth);

          //if (tabContainerWidth >= tabWidth) {
          //    $scope.hideButtons = false;
          //}

         

          bindHoldFunctionTo($leftNav, generateScrollFunction(realTabs, -tabScrollWidth));
          bindHoldFunctionTo($rightNav, generateScrollFunction(realTabs, tabScrollWidth));

          $scope.recalcSides();
        };

        var initAndApply = function() {
          init();
          $scope.$apply();          
        };

        //hello my friend jake weary
          $(window).on('resize', initAndApply);
        

        //we initialize by watching changes on the inner tabset's tabs collection
        var tabsetElem = angular.element($el[0].querySelector( 'div.spacer' )).children()[0];//get the wrapped 'tabset'
        var $tabsetElem = angular.element(tabsetElem);
        var tabsetScope = $tabsetElem.isolateScope() || $tabsetElem.scope();// get the tabset's scope to access to tabs collection

        $scope.$watchCollection(function(){
            return tabsetScope.tabs;
          }, function(newValues, oldValues){
            $timeout(initAndApply, 0);
        });

      }
    };
}]);
