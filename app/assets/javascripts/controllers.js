'use strict';

/** Controllers */
angular.module('liveChat.controllers', []).
    controller('ChatCtrl', function ($scope, $http) {
        $scope.messages = [];
        $scope.inputText = "";
        $scope.users = [];
        $scope.currentMessage = "";


        $scope.connect = function (username) {
            var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;
            $scope.chatSocket = new WS("ws://localhost:9000/room/chat/" + username);
            $scope.chatSocket.onmessage = $scope.receiveEvent;

        };

        $scope.sendMessage = function () {
            $scope.chatSocket.send(JSON.stringify(
                {text: $scope.currentMessage}
            ));
        }

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
            $scope.messages.push(data);
            $scope.users = data.members;
            $scope.$apply();
        }


        var alreadyExist = function (user) {
            var resp = false
            $(allMembers).each(function () {
                if (this == user)
                    resp = true
            })
            return resp
        }

        $scope.changeUsername = function (e) {
            var username = document.getElementById('username').value
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
                sendText("nickname:" + username)
            }
            $("#changeuser").modal('hide')
            updateDisplay()
        }


        $scope.handleUsernameReturnKey = function (e) {
            if (e.charCode == 13 || e.keyCode == 13) {
                e.preventDefault();
                $scope.changeUsername();
            }
        }

        $("#username").keypress($scope.handleUsernameReturnKey)
        $("#changeButton").click($scope.changeUsername)
        $("#username").focus()

        var myname = "@ChatRoom.username(username)"



    });