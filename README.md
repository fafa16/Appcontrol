# AppControl

Une seule appli Android, installée sur les deux téléphones.

- **Parent** : un gros bouton **Bloquer / Débloquer** la connexion de l'enfant.
- **Enfant** : un VPN local absorbe tout le trafic quand le parent bloque
  (Wi-Fi *et* données mobiles). L'appli elle-même reste connectée pour recevoir l'ordre de déblocage.

Les appels et SMS opérateur ne sont pas coupés (ils ne passent pas par l'IP).

## Mise en place (une seule fois)

1. Sur <https://console.firebase.google.com> : créer un projet, ajouter une appli Android
   avec le nom de package `com.appcontrol`, télécharger `google-services.json`
   et le placer dans `app/`.
2. Dans le projet Firebase : *Firestore Database* → créer la base, puis onglet *Règles* :

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{db}/documents {
       match /devices/{code} {
         allow get, write: if code.size() >= 12;   // le code secret fait office de mot de passe
         allow list: if false;
       }
     }
   }
   ```
3. Compiler l'APK :
   - Android Studio : ouvrir le dossier, *Build > Build APK*, ou `./gradlew assembleDebug` ;
   - ou GitHub : ajouter le contenu de `google-services.json` dans le secret
     `GOOGLE_SERVICES_JSON` du dépôt, l'action *Build APK* produit l'APK en artefact.
4. Installer l'APK sur les deux téléphones.

## Utilisation

1. Téléphone parent : choisir « PARENT » → un code de 12 caractères s'affiche.
2. Téléphone enfant : choisir « ENFANT », saisir le code, accepter la demande de VPN
   (une seule fois) et les notifications.
3. Parent : appuyer sur le bouton pour bloquer / débloquer.

## Protection côté enfant

- **Administrateur d'appareil** : à l'installation, l'enfant doit l'activer (écran Android).
  Tant qu'il est actif, la désinstallation est refusée ; il faut d'abord le désactiver dans les réglages.
- **Détection** : le téléphone de l'enfant envoie un signal toutes les minutes. L'écran parent affiche
  « HORS LIGNE depuis X min » si l'app est arrêtée/désinstallée/sans réseau, et un avertissement
  si l'administrateur est désactivé.

## Limites

L'enfant peut encore, s'il cherche, désactiver l'administrateur puis désinstaller, ou couper le VPN :
le parent en est seulement averti. Pour une vraie interdiction : mode Device Owner (nécessite un téléphone
réinitialisé) ou Google Family Link.
