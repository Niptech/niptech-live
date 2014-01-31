'use strict';

/** Controllers */
angular.module('liveChat.controllers', []).
    controller('ChatCtrl', function ($scope, $dialog, $http, $upload) {
        $scope.messages = [];
        $scope.inputText = "";
        $scope.users = [];
        $scope.currentMessage = "";
        $scope.username = "";
        $scope.currentUser = "";
        $scope.shouldScroll = true;


        $scope.connect = function (username) {
            var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;
            $scope.username = username;

            var url = jsRoutes.controllers.Application.chat(username).absoluteURL().replace("http://", "ws://")
            $scope.chatSocket = new WS(url);
            $scope.chatSocket.onmessage = $scope.receiveEvent;

            $http.get('/username/' + username).success(function (data, status, headers, config) {
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
            if (data.message == "//BANNI//")
                alert("Tu es banni de la chatroom")
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

        $scope.setUserStyle = function (username) {
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

        $scope.onFileDrop = function ($files) {
            for (var i = 0; i < $files.length; i++) {
                var file = $files[i];
                if (file.type.indexOf("image") == 0) {
                    $scope.upload = $upload.upload({
                        url: '/upload', //upload.php script, node.js route, or servlet url
                        // method: POST or PUT,
                        headers: {'username': $scope.username},
                        // withCredential: true,
                        // data: {myObj: $scope.myModelObj},
                        file: file
                    }).progress(function (evt) {
                        console.log('percent: ' + parseInt(100.0 * evt.loaded / evt.total));
                    }).success(function (data, status, headers, config) {
                        // file is uploaded successfully
                    });
                } else
                    alert("Vous ne pouvez déposer que des images");
            }
        }

        var myname = '';


    });


function ChangeNameDlgCtrl($scope, dialog) {
    $scope.close = function (result) {
        dialog.close(result);
    };
}
