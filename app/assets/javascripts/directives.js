'use strict';

function fakeNgModel(initValue){
    return {
        $setViewValue: function(value){
            this.$viewValue = value;
        },
        $viewValue: initValue
    };
}


angular.module('liveChat').directive('ngEnter', function () {
    return function (scope, element, attrs) {
        element.bind("keydown keypress", function (event) {
            if (event.which === 13) {
                scope.$apply(function () {
                    scope.$eval(attrs.ngEnter);
                });

                event.preventDefault();
                element.val('');
            }
        });
    };
});


angular.module('liveChat').directive('scrollGlue', function () {
    return {
        priority: 1,
        require: ['?ngModel'],
        restrict: 'A',
        link: function (scope, $el, attrs, ctrls) {
            var el = $el[0],
                ngModel = ctrls[0] || fakeNgModel(true);

            function scrollToBottom() {
                el.scrollTop = el.scrollHeight;
            }

            function shouldActivateAutoScroll() {
         //       return el.scrollTop + el.clientHeight == el.scrollHeight;
                return scope.shouldScroll;
            }

            scope.$watch(function () {
                if (ngModel.$viewValue) {
                    scrollToBottom();
                }
            });

            $el.bind('scroll', function () {
                scope.$apply(ngModel.$setViewValue.bind(ngModel, shouldActivateAutoScroll()));
            });
        }
    };
});