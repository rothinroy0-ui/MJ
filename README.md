# MG Android App

APK banane ke 2 tareeke:

## Tareeka 1: GitHub se (phone se bhi ho jata hai, sabse aasan)
1. github.com par free account banao -> New repository (naam: MG).
2. Is folder ki saari files (".github" folder samet) us repo mein upload karo.
3. Repo mein **Actions** tab -> **Build APK** -> **Run workflow**.
4. 3-5 minute baad run khulega -> neeche **Artifacts** se **MG-apk** download karo -> zip kholo -> `app-debug.apk` install karo.

## Tareeka 2: Android Studio se
Folder kholo -> Build > Build APK(s). APK: app/build/outputs/apk/debug/app-debug.apk

## AI features
App pehli baar AI wala kaam karte waqt Anthropic API key maangega (console.anthropic.com). Bina key ke notes, timer, to-do, expenses, QR, backup jaise offline features chalte hain.
