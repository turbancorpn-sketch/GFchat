# GFchat

Do logo (aap aur aapki girlfriend) ke liye private Android chat app.
Kotlin + Jetpack Compose + Material 3.

## Stage 1 (ye wala) – kya bana hai

- **Secret code lock**: pehli baar 4-digit code banta hai (2 baar daalna padta hai). Code kabhi plain text me save nahi hota (salt + PBKDF2 hash). 5 galat try ke baad 30 second ka lockout.
- **Fingerprint unlock**: phone me fingerprint set ho to app khulte hi fingerprint prompt aata hai, nahi to secret code.
- **Auto-lock**: app 15 second se zyada background me rahe to dobara lock (`RELOCK_AFTER_MS` in `MainActivity.kt`).
- **Screenshot band**: `FLAG_SECURE` se screenshot aur recent-apps preview band.
- **Chat UI**: text, photo (gallery se), video (gallery se) messages, bubbles, time.
- **Audio call aur video call screen**: mute, speaker, camera on/off, timer.
- Girlfriend ka naam badalne ke liye `PARTNER_NAME` in `MainActivity.kt`.

## Abhi kya NAHI chalta (sach me)

- Messages demo data hain aur sirf aapke phone me dikhte hain. **Abhi dusre phone tak kuch nahi jaata.**
- Photo/video abhi sirf aapki chat screen me dikhte hain, upload nahi hote.
- Call screens sirf UI hain: awaaz aur video nahi jaati.

## Aage ke stages

1. **Stage 2** – Real messaging: dono phones ka login/pairing (sirf 2 log), real-time text, photo/video upload (backend: Supabase Auth + Realtime + private Storage), push notification (FCM).
2. **Stage 3** – Real audio + video call (WebRTC, signaling + TURN server), camera/mic permissions.
3. **Stage 4** – Polish: end-to-end encryption, voice messages, read receipts, Hindi UI.

## Chalane ka tareeka

1. Android Studio (Koala ya naya) me is folder ko open karo; Gradle sync hone do (wrapper Android Studio khud banata hai).
2. Phone ya emulator pe Run karo (minSdk 26).
3. Test: pehli baar code banao, app band karke 15 second baad kholo → lock aana chahiye; fingerprint ke liye phone me fingerprint set hona chahiye.

Note: ye code yahan compile/test nahi kiya gaya (Android SDK available nahi tha), isliye pehli build me chhoti errors aa sakti hain; bata dena, theek kar dunga.
