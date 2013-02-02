# Application Live Chat pour NipTech
* *Auteur* : [Fabrice Croiseaux](http://twitter.com/fXzo)
* *Technologies* : Play 2.1, Scala

Cette application gère un **chat live** ainsi que la **diffusion d'un flux youtube** sur la même page.

## ADMINISTRATION
L'administration consiste à indiquer l'id du flux youtube à diffuser et à activer ou désactiver le live stream Twitter.
La page d'administration est sécurisée et est accessible à http://live.niptech.com/admin
* Pour démarrer le chat, cliquer sur le bouton rouge **Off Air** et indiquer l'id du flux youtube
* L'activation/désactivation du twitter live stream se fait via le bouton éponyme.
* Pour appliquer les modifications, cliquer sur **Appliquer**

## TWITTER LIVESTREAM
Le Twitter LiveStream permet aux utilisateurs du chat d'interagir avec Twitter :
* Si sur twitter, un message contenant @niptechlive est posté, il s'affiche dans la chat room. Ainsi, ceux qui n'ont pas la possibilité d'assister au live peuvent envoyer des messages aux personnes présentes.
* Si la fonctionnalité est activée par Ben, tous les messages du live peuvent être diffusés en temps réel sur Twitter. Ils sont postés par l'utilisateur @niptechlive.
* Un utilisateur peut se connecter via OAuth et son compte twitter, son avatar s'affiche dans la chat room. Sinon, il est possible de ne saisir qu'un pseudo.


## Licensing
<a rel="license" href="http://creativecommons.org/licenses/by/3.0/deed.fr"><img alt="Licence Creative Commons" style="border-width:0" src="http://i.creativecommons.org/l/by/3.0/88x31.png" /></a><br /><span xmlns:dct="http://purl.org/dc/terms/" property="dct:title">Niptech Chat Room</span> est mis à disposition selon les termes de la <a rel="license" href="http://creativecommons.org/licenses/by/3.0/deed.fr">licence Creative Commons Attribution 3.0 non transposé</a>.

