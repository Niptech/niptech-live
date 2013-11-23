'use strict';

/** Controllers */
angular.module('liveChat.controllers', []).
    controller('ChatCtrl', function ($scope, $dialog, $http) {
        $scope.messages = [];
        $scope.inputText = "";
        $scope.users = [];
        $scope.currentMessage = "";
        $scope.currentUser ="";
        $scope.title= "dissèque la tech";


        $scope.connect = function (username) {
            var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;
          //  $scope.chatSocket = new WS("ws://localhost:9000/room/chat/" + username);

         var url = jsRoutes.controllers.Application.chat(username).absoluteURL().replace("http://","ws://")
         $scope.chatSocket = new WS(url);
            $scope.chatSocket.onmessage = $scope.receiveEvent;

            $http.get('/username/' + username).success(function(data, status, headers, config) {
                $scope.currentUser = data;
            });
        };

        $scope.sendText = function (textMsg) {
            $scope.chatSocket.send(JSON.stringify(
                {text: textMsg}
            ))
        }

        $scope.receiveEvent = function (event) {
            var data = JSON.parse(event.data)

            // Handle errors
            if (data.error) {
                chatSocket.close()
                $("#onError span").text(data.error)
                $("#onError").show();
                return
            } else {
                $("#onChat").show();
            }

            var now = new Date();
            var min = now.getMinutes() + "";
            if (min.length == 1) {
                min = "0" + min;
            }
            data.time = now.getHours() + ':' + min;
            if (data.user == "GAGNANT")
                alert("Tu es GAGNANT. Le mot à donner comme preuve: " + data.message)
            else if (data.message != "") {
            	$scope.messages.push(data);
            }
            $scope.users = data.members;
            $scope.$apply();
        }


        var alreadyExist = function (user) {
            var resp = false
            $($scope.users).each(function () {
                if (this == user)
                    resp = true
            })
            return resp
        }

        $scope.changeUsername = function (username) {
            if (username.indexOf("<") != -1) {
                showErrorMsg("Si tu veux programmer en HTML, aide nous plutôt à faire évoluer le chat")
            } else if (username.indexOf("@@") != -1) {
                showErrorMsg("Le caractère @@ est réservé pour les comptes twitters")
            } else if (alreadyExist(username)) {
                showErrorMsg("Utilisateur déjà existant")
            } else if (username.trim() == "") {
                showErrorMsg("Merci de saisir un nom d'utilisateur")
            } else {
                myname = username
                $scope.sendText("nickname:" + username)
                $scope.currentUser = username;
            }
        }

        $scope.setUserStyle = function(username) {
            if (username == $scope.currentUser) {
                return "{color : 'blue';}";
            } else {
                return "{}";
            }
        }

        $scope.openChangeUsernameDlg = function () {
            var dialogOptions = {
                modal: true,
                fade: true,
                backdrop: true,
                keyboard: true,
                backdropClick: true,
                templateUrl: '/changeuser',
                controller: 'ChangeNameDlgCtrl'
            };

            var d = $dialog.dialog(dialogOptions);
            d.open().then(function (username) {
                if (username) {
                    $scope.changeUsername(username);
                }
            });
        }

        var myname = '';


    });


function ChangeNameDlgCtrl($scope, dialog) {
    $scope.close = function(result) {
        dialog.close(result);
    };
}
